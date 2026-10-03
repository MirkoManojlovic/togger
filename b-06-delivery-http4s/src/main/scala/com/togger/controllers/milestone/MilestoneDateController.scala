package com.togger.controllers.milestone

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.{DateRangeValidation, TgJsonSuccess}
import com.togger.controllers.milestone.MilestoneDateDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindMilestoneDateResp, MilestoneDateVersionDto}
import com.togger.milestone.{
  MilestoneDate,
  MilestoneDateEndpoints,
  MilestoneDateService,
  MilestoneDateVersion,
  MilestoneService
}

import java.time.ZoneOffset
import java.util.UUID

class MilestoneDateController(
  milestoneDateEndpoints: MilestoneDateEndpoints,
  milestoneDateService: MilestoneDateService,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation
) {

  val save = milestoneDateEndpoints.saveEndpoint.serverLogic { auth => dto =>
    milestoneService.getCurrent(dto.milestoneId).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneDateResp])
      case Right(milestone) =>
        val dateAsLocalDate = dto.date.atOffset(ZoneOffset.UTC).toLocalDate
        dateRangeValidation
          .validateOnOrAfterStart("Date", dateAsLocalDate, milestone.version.startDate)
          .toEither match {
          case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneDateResp])
          case Right(_) =>
            val now = java.time.Instant.now()
            val milestoneDate = MilestoneDate(
              id = UUID.randomUUID(),
              milestoneId = dto.milestoneId,
              createdAt = now
            )
            val firstVersion = MilestoneDateVersion(
              version = UUID.randomUUID(),
              milestoneDateId = milestoneDate.id,
              name = dto.name,
              description = dto.description,
              date = dto.date,
              createdAt = now,
              updatedAt = now
            )

            milestoneDateService
              .createMilestoneDate(milestoneDate, firstVersion)
              .map(result => TgJsonSuccess.justBodyRight(toDto(result.milestoneDate, result.version)))
        }
    }
  }

  val findById = milestoneDateEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    milestoneDateService
      .getCurrent(id)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestoneDate, result.version))
        case Left(error)   => error.toBadRequestLeft[FindMilestoneDateResp]
      }
  }

  val findByMilestoneId = milestoneDateEndpoints.findByMilestoneIdEndpoint.serverLogic { auth => milestoneId =>
    milestoneDateService
      .findByMilestoneId(milestoneId)
      .map(milestoneDatesWithLatestVersion =>
        TgJsonSuccess.justBodyRight(milestoneDatesWithLatestVersion.map(m => toDto(m.milestoneDate, m.versions.head)))
      )
  }

  val findByProjectId = milestoneDateEndpoints.findByProjectIdEndpoint.serverLogic { auth => projectId =>
    milestoneDateService
      .findByProjectId(projectId)
      .map(datesWithLatestVersion =>
        TgJsonSuccess.justBodyRight(datesWithLatestVersion.map(m => toDto(m.milestoneDate, m.versions.head)))
      )
  }

  val update = milestoneDateEndpoints.updateEndpoint.serverLogic { auth => dto =>
    milestoneDateService.getCurrent(dto.id).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneDateResp])
      case Right(currentMilestoneDate) =>
        milestoneService.getCurrent(currentMilestoneDate.milestoneDate.milestoneId).flatMap {
          case Left(error) => IO.pure(error.toBadRequestLeft[FindMilestoneDateResp])
          case Right(milestone) =>
            val dateAsLocalDate = dto.date.atOffset(ZoneOffset.UTC).toLocalDate
            dateRangeValidation
              .validateOnOrAfterStart("Date", dateAsLocalDate, milestone.version.startDate)
              .toEither match {
              case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneDateResp])
              case Right(_) =>
                val now = java.time.Instant.now()
                val newVersion = MilestoneDateVersion(
                  version = UUID.randomUUID(),
                  milestoneDateId = dto.id,
                  name = dto.name,
                  description = dto.description,
                  date = dto.date,
                  createdAt = now,
                  updatedAt = now
                )

                milestoneDateService
                  .saveNewVersion(dto.id, newVersion)
                  .map {
                    case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestoneDate, result.version))
                    case Left(error)   => error.toBadRequestLeft[FindMilestoneDateResp]
                  }
            }
        }
    }
  }

  val delete = milestoneDateEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    milestoneDateService
      .deleteMilestoneDate(id)
      .map(_ => TgJsonSuccess.justMessageRight("Milestone date deleted successfully"))
  }

  val listVersions = milestoneDateEndpoints.listVersionsEndpoint.serverLogic { auth => milestoneDateId =>
    milestoneDateService
      .listVersions(milestoneDateId)
      .map(versions => TgJsonSuccess.justBodyRight(versions.map(toVersionDto)))
  }

  val updateVersion = milestoneDateEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    milestoneDateService.getCurrent(dto.milestoneDateId).flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[MilestoneDateVersionDto])
      case Right(currentMilestoneDate) =>
        milestoneService.getCurrent(currentMilestoneDate.milestoneDate.milestoneId).flatMap {
          case Left(error) => IO.pure(error.toBadRequestLeft[MilestoneDateVersionDto])
          case Right(milestone) =>
            val dateAsLocalDate = dto.date.atOffset(ZoneOffset.UTC).toLocalDate
            dateRangeValidation
              .validateOnOrAfterStart("Date", dateAsLocalDate, milestone.version.startDate)
              .toEither match {
              case Left(errors) => IO.pure(errors.toBadRequestLeft[MilestoneDateVersionDto])
              case Right(_) =>
                milestoneDateService
                  .updateVersionContent(dto.version, dto.name, dto.description, dto.date)
                  .map {
                    case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
                    case Left(error)    => error.toBadRequestLeft[MilestoneDateVersionDto]
                  }
            }
        }
    }
  }

  val deleteVersion = milestoneDateEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    milestoneDateService
      .deleteVersion(version)
      .map {
        case Right(_)    => TgJsonSuccess.justMessageRight("Milestone date version deleted successfully")
        case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
      }
  }
}
