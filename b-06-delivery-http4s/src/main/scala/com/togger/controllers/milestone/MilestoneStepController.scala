package com.togger.controllers.milestone

import cats.data.EitherNec
import cats.data.NonEmptyChain
import cats.effect.IO
import cats.implicits._
import com.togger.common.DateRangeValidation
import com.togger.common.TgClock
import com.togger.common.TgError
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgResponseTypes.TgResp
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.milestone.MilestoneStepDtoMapper.toDto
import com.togger.controllers.milestone.MilestoneStepDtoMapper.toVersionDto
import com.togger.controllers.project.ProjectService
import com.togger.dto.CreateMilestoneStepReq
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.UpdateMilestoneStepReq
import com.togger.milestone.MilestoneStepError.ChangeCommentEmpty
import com.togger.milestone.MilestoneStepError.DefaultStepNameReserved
import com.togger.milestone.MilestoneStepError.EndDateRequiredWhenClosed
import com.togger.milestone._

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

class MilestoneStepController(
  milestoneStepEndpoints: MilestoneStepEndpoints,
  milestoneStepService: MilestoneStepService,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation,
  projectService: ProjectService,
  tgClock: TgClock
) {

  /** The latest version of a milestone and the work days of its project, which every step date is checked against. */
  private case class MilestoneContext(version: MilestoneVersion, workDays: Set[DayOfWeek])

  private def withMilestoneContext[T](milestoneId: UUID)(block: MilestoneContext => IO[TgResp[T]]): IO[TgResp[T]] =
    milestoneService.getCurrent(milestoneId).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[T])
      case Right(milestone) =>
        projectService
          .findById(milestone.milestone.projectId)
          .flatMap { case (project, _, _) => block(MilestoneContext(milestone.version, project.workDays)) }
    }

  private def validateComment(comment: String): EitherNec[TgError, Unit] =
    Either.cond(comment.trim.nonEmpty, (), NonEmptyChain.one[TgError](ChangeCommentEmpty))

  private def validateClosedHasEndDate(
    status: MilestoneStepStatus,
    endDate: Option[LocalDate]
  ): EitherNec[TgError, Unit] =
    Either.cond(!status.isClosing || endDate.isDefined, (), NonEmptyChain.one[TgError](EndDateRequiredWhenClosed))

  private def validateDates(
    context: MilestoneContext,
    startDate: LocalDate,
    dueDate: LocalDate,
    endDate: Option[LocalDate]
  ): EitherNec[TgError, Unit] =
    List(
      dateRangeValidation.validate(Some(startDate), Some(dueDate), endDate, context.workDays),
      dateRangeValidation.validateWithinRange(startDate, dueDate, context.version.startDate, context.version.dueDate)
    ).traverse_(_.toValidated).toEither

  private def validateUpdate(context: MilestoneContext, dto: UpdateMilestoneStepReq): EitherNec[TgError, Unit] =
    List(
      validateDates(context, dto.startDate, dto.dueDate, dto.endDate),
      validateClosedHasEndDate(dto.status, dto.endDate),
      validateComment(dto.changeComment)
    ).traverse_(_.toValidated).toEither

  private def validateNotDefaultName(name: String): EitherNec[TgError, Unit] =
    Either.cond(
      !DefaultMilestoneStep.isDefaultName(name),
      (),
      NonEmptyChain.one(DefaultStepNameReserved(name.trim): TgError)
    )

  val save = milestoneStepEndpoints.saveEndpoint.serverLogic { auth => dto =>
    withMilestoneContext[FindMilestoneStepResp](dto.milestoneId) { context =>
      validateDates(context, dto.startDate, dto.dueDate, None).flatMap(_ => validateNotDefaultName(dto.name)) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneStepResp])
        case Right(_)     => createStep(dto)
      }
    }
  }

  private def createStep(dto: CreateMilestoneStepReq): IO[TgResp[FindMilestoneStepResp]] = {
    val now = tgClock.currentInstant
    val step = MilestoneStep(UUID.randomUUID(), dto.milestoneId, now)
    val firstVersion = MilestoneStepVersion(
      version = UUID.randomUUID(),
      milestoneStepId = step.id,
      name = dto.name,
      description = dto.description,
      status = MilestoneStepStatus.Planned,
      position = 0,
      startDate = dto.startDate,
      dueDate = dto.dueDate,
      endDate = None,
      createdAt = now,
      updatedAt = now,
      changeComment = ""
    )
    milestoneStepService
      .createMilestoneStep(step, firstVersion, dto.position, now)
      .map(result => TgJsonSuccess.justBodyRight(toDto(result)))
  }

  /** The steps with the number of tasks in each. */
  private def withTaskCounts(steps: List[MilestoneStepWithOneVersion]): IO[List[FindMilestoneStepResp]] =
    milestoneStepService
      .taskCountsByStepIds(steps.map(_.milestoneStep.id))
      .map(counts =>
        steps.map(step => toDto(step, counts.getOrElse(step.milestoneStep.id, MilestoneStepTaskCounts.empty)))
      )

  val findById = milestoneStepEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    milestoneStepService.getCurrent(id).flatMap {
      case Right(result) => withTaskCounts(List(result)).map(found => TgJsonSuccess.justBodyRight(found.head))
      case Left(error)   => IO.pure(error.toBadRequestLeft[FindMilestoneStepResp])
    }
  }

  val findByMilestoneId = milestoneStepEndpoints.findByMilestoneIdEndpoint.serverLogic { auth => milestoneId =>
    milestoneStepService
      .findAllByMilestoneId(milestoneId)
      .flatMap(withTaskCounts)
      .map(steps => TgJsonSuccess.justBodyRight(steps))
  }

  val findByProjectId = milestoneStepEndpoints.findByProjectIdEndpoint.serverLogic { auth => projectId =>
    milestoneStepService
      .findAllByProjectId(projectId)
      .flatMap(withTaskCounts)
      .map(steps => TgJsonSuccess.justBodyRight(steps))
  }

  val update = milestoneStepEndpoints.updateEndpoint.serverLogic { auth => dto =>
    milestoneStepService.getCurrent(dto.id).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneStepResp])
      case Right(current) =>
        withMilestoneContext[FindMilestoneStepResp](current.milestoneStep.milestoneId) { context =>
          validateUpdate(context, dto) match {
            case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneStepResp])
            case Right(_)     => saveNewVersion(dto, current.version)
          }
        }
    }
  }

  private def saveNewVersion(
    dto: UpdateMilestoneStepReq,
    current: MilestoneStepVersion
  ): IO[TgResp[FindMilestoneStepResp]] = {
    val now = tgClock.currentInstant
    val newVersion = current.copy(
      version = UUID.randomUUID(),
      name = dto.name,
      description = dto.description,
      status = dto.status,
      startDate = dto.startDate,
      dueDate = dto.dueDate,
      endDate = dto.endDate,
      createdAt = now,
      updatedAt = now,
      changeComment = dto.changeComment.trim
    )
    milestoneStepService.saveNewVersion(dto.id, newVersion).flatMap {
      case Right(result) => withTaskCounts(List(result)).map(found => TgJsonSuccess.justBodyRight(found.head))
      case Left(error)   => IO.pure(error.toBadRequestLeft[FindMilestoneStepResp])
    }
  }

  val reorder = milestoneStepEndpoints.reorderEndpoint.serverLogic { auth => dto =>
    validateComment(dto.changeComment) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[List[FindMilestoneStepResp]])
      case Right(_) =>
        milestoneStepService
          .reorder(dto.milestoneId, dto.stepIds, dto.changeComment.trim, tgClock.currentInstant)
          .flatMap {
            case Right(steps) => withTaskCounts(steps).map(found => TgJsonSuccess.justBodyRight(found))
            case Left(error)  => IO.pure(error.toBadRequestLeft[List[FindMilestoneStepResp]])
          }
    }
  }

  val delete = milestoneStepEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    milestoneStepService.deleteMilestoneStep(id).map {
      case Right(_)    => TgJsonSuccess.justMessageRight("Milestone step deleted successfully")
      case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
    }
  }

  val listVersions = milestoneStepEndpoints.listVersionsEndpoint.serverLogic { auth => request =>
    val (milestoneStepId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      milestoneStepService
        .listVersions(milestoneStepId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

}
