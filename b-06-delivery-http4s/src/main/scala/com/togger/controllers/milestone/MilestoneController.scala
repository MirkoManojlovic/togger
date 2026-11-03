package com.togger.controllers.milestone

import cats.data.EitherNec
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.implicits._
import com.togger.common.DateRangeValidation
import com.togger.common.TgError
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgResponseTypes.TgResp
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.milestone.MilestoneDtoMapper.toDto
import com.togger.controllers.milestone.MilestoneDtoMapper.toVersionDto
import com.togger.controllers.project.ProjectService
import com.togger.dto.CreateMilestoneReq
import com.togger.dto.FindMilestoneResp
import com.togger.dto.MilestoneVersionDto
import com.togger.dto.NewMilestoneStepReq
import com.togger.dto.UpdateMilestoneReq
import com.togger.milestone.DefaultMilestoneStep
import com.togger.milestone.Milestone
import com.togger.milestone.MilestoneEndpoints
import com.togger.milestone.MilestoneError.InvalidStep
import com.togger.milestone.MilestoneError.MilestoneHasTasks
import com.togger.milestone.MilestoneError.StepsOutsideNewRange
import com.togger.milestone.MilestoneService
import com.togger.milestone.MilestoneStatus
import com.togger.milestone.MilestoneStep
import com.togger.milestone.MilestoneStepCounts
import com.togger.milestone.MilestoneStepError.DefaultStepNameReserved
import com.togger.milestone.MilestoneStepService
import com.togger.milestone.MilestoneStepStatus
import com.togger.milestone.MilestoneStepVersion
import com.togger.milestone.MilestoneVersion
import com.togger.milestone.MilestoneWithOneVersion
import com.togger.milestone.MilestoneWithStepsService

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class MilestoneController(
  milestoneEndpoints: MilestoneEndpoints,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation,
  projectService: ProjectService,
  milestoneStepService: MilestoneStepService,
  milestoneWithStepsService: MilestoneWithStepsService
) {

  val save = milestoneEndpoints.saveEndpoint.serverLogic { _ => dto =>
    projectService.findById(dto.projectId).flatMap { case (project, _, _) =>
      validateNewMilestone(dto, project.workDays) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneResp])
        case Right(_)     => createMilestone(dto)
      }
    }
  }

  private def validateNewMilestone(dto: CreateMilestoneReq, workDays: Set[DayOfWeek]): EitherNec[TgError, Unit] =
    (
      dateRangeValidation.validate(Some(dto.startDate), Some(dto.dueDate), None, workDays).toValidated,
      dto.steps.traverse_(step => validateNewStep(step, dto, workDays).toValidated)
    ).mapN((_, _) => ()).toEither

  private def validateNewStep(
    step: NewMilestoneStepReq,
    dto: CreateMilestoneReq,
    workDays: Set[DayOfWeek]
  ): EitherNec[TgError, Unit] =
    List(
      dateRangeValidation.validate(Some(step.startDate), Some(step.dueDate), None, workDays),
      dateRangeValidation.validateWithinRange(step.startDate, step.dueDate, dto.startDate, dto.dueDate)
    ).traverse_(_.toValidated).toEither.leftMap(_.map(error => InvalidStep(step.name, error.message): TgError)) *>
      Either.cond(
        !DefaultMilestoneStep.isDefaultName(step.name),
        (),
        NonEmptyChain.one(DefaultStepNameReserved(step.name.trim): TgError)
      )

  private def createMilestone(dto: CreateMilestoneReq): IO[TgResp[FindMilestoneResp]] = {
    val now = java.time.Instant.now()
    val milestone = Milestone(
      id = UUID.randomUUID(),
      projectId = dto.projectId,
      createdAt = now
    )
    val firstVersion = MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = milestone.id,
      name = dto.name,
      description = dto.description,
      status = MilestoneStatus.InProgress,
      startDate = dto.startDate,
      dueDate = dto.dueDate,
      endDate = None,
      createdAt = now,
      updatedAt = now
    )
    val steps = dto.steps.map { step =>
      val stepEntity = MilestoneStep(UUID.randomUUID(), milestone.id, now)
      stepEntity -> MilestoneStepVersion(
        version = UUID.randomUUID(),
        milestoneStepId = stepEntity.id,
        name = step.name,
        description = step.description,
        status = MilestoneStepStatus.Planned,
        position = 0,
        startDate = step.startDate,
        dueDate = step.dueDate,
        endDate = None,
        createdAt = now,
        updatedAt = now,
        changeComment = ""
      )
    }

    milestoneWithStepsService
      .create(milestone, firstVersion, steps)
      .map { case (created, createdSteps) =>
        TgJsonSuccess.justBodyRight(
          toDto(created.milestone, created.version, MilestoneStepCounts(createdSteps.size, 0))
        )
      }
  }

  val findById = milestoneEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    milestoneService
      .getCurrent(id)
      .flatMap {
        case Right(result) =>
          stepCounts(List(id))
            .map(counts => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version, counts(id))))
        case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneResp])
      }
  }

  val findByProjectId = milestoneEndpoints.findByProjectIdEndpoint.serverLogic { _ => request =>
    val (projectId, filter, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      milestoneService
        .findPaginatedByProjectId(projectId, filter, page)
        .flatMap { milestonesPage =>
          stepCounts(milestonesPage.items.map(_.milestone.id).toList).map(counts =>
            TgJsonSuccess.justBodyRight(
              milestonesPage.mapItem(m => toDto(m.milestone, m.versions.head, counts(m.milestone.id)))
            )
          )
        }
    }
  }

  val update = milestoneEndpoints.updateEndpoint.serverLogic { _ => dto =>
    milestoneService.getCurrent(dto.id).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneResp])
      case Right(current) =>
        val effectiveStartDate = dto.startDate.getOrElse(current.version.startDate)
        val effectiveDueDate = dto.dueDate.getOrElse(current.version.dueDate)
        val effectiveEndDate = dto.endDate.orElse(current.version.endDate)

        projectService.findById(current.milestone.projectId).flatMap { case (project, _, _) =>
          dateRangeValidation.validate(
            Some(effectiveStartDate),
            Some(effectiveDueDate),
            effectiveEndDate,
            project.workDays
          ) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneResp])
            case Right(_) =>
              milestoneStepService.findStepsOutsideRange(dto.id, effectiveStartDate, effectiveDueDate).flatMap {
                case outside if outside.nonEmpty =>
                  IO.pure(
                    StepsOutsideNewRange(outside.map(_.name), effectiveStartDate, effectiveDueDate)
                      .toBadRequestLeft[FindMilestoneResp]
                  )
                case _ => saveUpdatedVersion(dto, current, effectiveStartDate, effectiveDueDate, effectiveEndDate)
              }
          }
        }
    }
  }

  private def saveUpdatedVersion(
    dto: UpdateMilestoneReq,
    current: MilestoneWithOneVersion,
    effectiveStartDate: LocalDate,
    effectiveDueDate: LocalDate,
    effectiveEndDate: Option[LocalDate]
  ): IO[TgResp[FindMilestoneResp]] = {
    val now = java.time.Instant.now()
    val newVersion = MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = dto.id,
      name = dto.name,
      description = dto.description,
      status = dto.status.getOrElse(current.version.status),
      startDate = effectiveStartDate,
      dueDate = effectiveDueDate,
      endDate = effectiveEndDate,
      createdAt = now,
      updatedAt = now
    )

    milestoneService
      .saveNewVersion(dto.id, newVersion)
      .flatMap {
        case Right(result) =>
          stepCounts(List(dto.id))
            .map(counts => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version, counts(dto.id))))
        case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneResp])
      }
  }

  private def stepCounts(milestoneIds: List[UUID]): IO[Map[UUID, MilestoneStepCounts]] =
    milestoneStepService.countsByMilestoneIds(milestoneIds)

  val delete = milestoneEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    milestoneStepService.countTasksOfMilestone(id).flatMap {
      case tasks if tasks > 0 => IO.pure(MilestoneHasTasks(tasks).toBadRequestLeft[TgJsonSuccess.TgEmptyBody])
      case _ =>
        milestoneService
          .deleteMilestone(id)
          .map(_ => TgJsonSuccess.justMessageRight("Milestone deleted successfully"))
    }
  }

  val listVersions = milestoneEndpoints.listVersionsEndpoint.serverLogic { _ => request =>
    val (milestoneId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      milestoneService
        .listVersions(milestoneId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val updateVersion = milestoneEndpoints.updateVersionEndpoint.serverLogic { _ => dto =>
    milestoneService.findProjectIdByVersion(dto.version).flatMap { projectId =>
      projectService.findById(projectId).flatMap { case (project, _, _) =>
        val validation = for {
          _ <- dateRangeValidation.validate(Some(dto.startDate), Some(dto.dueDate), dto.endDate, project.workDays)
          _ <- dateRangeValidation.validateEndDateWhenDone(dto.status == MilestoneStatus.Done, dto.endDate)
        } yield ()
        validation match {
          case Left(errors) => IO.pure(errors.toBadRequestLeft[MilestoneVersionDto])
          case Right(_) =>
            milestoneService
              .updateVersionContent(
                dto.version,
                dto.name,
                dto.description,
                dto.status,
                dto.startDate,
                dto.dueDate,
                dto.endDate
              )
              .map {
                case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
                case Left(error)    => error.toBadRequestLeft[MilestoneVersionDto]
              }
        }
      }
    }
  }

  val deleteVersion = milestoneEndpoints.deleteVersionEndpoint.serverLogic { _ => version =>
    milestoneService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Milestone version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
