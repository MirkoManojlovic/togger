package com.togger.controllers.milestone

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.milestone.MilestoneDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindMilestoneResp, MilestoneVersionDto}
import com.togger.milestone.{
  Milestone,
  MilestoneDateValidation,
  MilestoneEndpoints,
  MilestoneService,
  MilestoneStatus,
  MilestoneVersion
}

import java.util.UUID

class MilestoneController(
  milestoneEndpoints: MilestoneEndpoints,
  milestoneService: MilestoneService,
  milestoneDateValidation: MilestoneDateValidation
) {

  val save = milestoneEndpoints.saveEndpoint.serverLogic { auth => dto =>
    milestoneDateValidation.validate(Some(dto.startDate), Some(dto.estimatedEndDate), None) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneResp])
      case Right(_) =>
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
          estimatedEndDate = dto.estimatedEndDate,
          endDate = None,
          createdAt = now,
          updatedAt = now
        )

        milestoneService
          .createMilestone(milestone, firstVersion)
          .map(result => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version)))
    }
  }

  val findById = milestoneEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    milestoneService
      .getCurrent(id)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version))
        case Left(error)   => error.toBadRequestLeft[FindMilestoneResp]
      }
  }

  val findByProjectId = milestoneEndpoints.findByProjectIdEndpoint.serverLogic { auth => projectId =>
    milestoneService
      .findByProjectId(projectId)
      .map(milestonesWithLatestVersion =>
        TgJsonSuccess.justBodyRight(milestonesWithLatestVersion.map(m => toDto(m.milestone, m.versions.head)))
      )
  }

  val update = milestoneEndpoints.updateEndpoint.serverLogic { auth => dto =>
    milestoneDateValidation.validate(dto.startDate, dto.estimatedEndDate, dto.endDate) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneResp])
      case Right(_) =>
        milestoneService.getCurrent(dto.id).flatMap {
          case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneResp])
          case Right(current) =>
            val now = java.time.Instant.now()
            val newVersion = MilestoneVersion(
              version = UUID.randomUUID(),
              milestoneId = dto.id,
              name = dto.name,
              description = dto.description,
              status = dto.status.getOrElse(current.version.status),
              startDate = dto.startDate.getOrElse(current.version.startDate),
              estimatedEndDate = dto.estimatedEndDate.getOrElse(current.version.estimatedEndDate),
              endDate = dto.endDate.orElse(current.version.endDate),
              createdAt = now,
              updatedAt = now
            )

            milestoneService
              .saveNewVersion(dto.id, newVersion)
              .map {
                case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestone, result.version))
                case Left(error)   => error.toBadRequestLeft[FindMilestoneResp]
              }
        }
    }
  }

  val delete = milestoneEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    milestoneService
      .deleteMilestone(id)
      .map(_ => TgJsonSuccess.justMessageRight("Milestone deleted successfully"))
  }

  val listVersions = milestoneEndpoints.listVersionsEndpoint.serverLogic { auth => milestoneId =>
    milestoneService
      .listVersions(milestoneId)
      .map(versions => TgJsonSuccess.justBodyRight(versions.map(toVersionDto)))
  }

  val updateVersion = milestoneEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    milestoneDateValidation.validate(Some(dto.startDate), Some(dto.estimatedEndDate), dto.endDate) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[MilestoneVersionDto])
      case Right(_) =>
        milestoneService
          .updateVersionContent(
            dto.version,
            dto.name,
            dto.description,
            dto.status,
            dto.startDate,
            dto.estimatedEndDate,
            dto.endDate
          )
          .map {
            case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
            case Left(error)    => error.toBadRequestLeft[MilestoneVersionDto]
          }
    }
  }

  val deleteVersion = milestoneEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    milestoneService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Milestone version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
