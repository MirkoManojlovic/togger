package com.togger.controllers.milestone

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgAuth
import com.togger.common.{DateRangeValidation, Forbidden, TgError, TgJsonFailure, TgJsonSuccess}
import com.togger.contribution.ContributionService
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.milestone.MilestoneSummaryDtoMapper.{toDto, toVersionDto}
import com.togger.dto.{FindMilestoneSummaryResp, MilestoneSummaryVersionDto}
import com.togger.milestone.{
  MilestoneService,
  MilestoneSummary,
  MilestoneSummaryEndpoints,
  MilestoneSummaryService,
  MilestoneSummaryVersion
}

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryController(
  milestoneSummaryEndpoints: MilestoneSummaryEndpoints,
  milestoneSummaryService: MilestoneSummaryService,
  milestoneService: MilestoneService,
  dateRangeValidation: DateRangeValidation,
  contributionService: ContributionService
) {

  val save = milestoneSummaryEndpoints.saveEndpoint.serverLogic { auth => dto =>
    withProjectMember[FindMilestoneSummaryResp](milestoneService.findProjectId(dto.milestoneId).map(_.asRight), auth) {
      validateDates(dto.startDate, dto.endDate) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneSummaryResp])
        case Right(_) =>
          val now = java.time.Instant.now()
          val milestoneSummary = MilestoneSummary(
            id = UUID.randomUUID(),
            milestoneId = dto.milestoneId,
            createdAt = now
          )
          val firstVersion = MilestoneSummaryVersion(
            version = UUID.randomUUID(),
            milestoneSummaryId = milestoneSummary.id,
            summary = dto.summary,
            startDate = dto.startDate,
            endDate = dto.endDate,
            createdAt = now,
            updatedAt = now
          )

          milestoneSummaryService
            .createMilestoneSummary(milestoneSummary, firstVersion)
            .map(result => TgJsonSuccess.justBodyRight(toDto(result.milestoneSummary, result.version)))
      }
    }
  }

  val findById = milestoneSummaryEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    withProjectMember[FindMilestoneSummaryResp](projectIdOfSummary(id), auth) {
      milestoneSummaryService
        .getCurrent(id)
        .map {
          case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestoneSummary, result.version))
          case Left(error)   => error.toBadRequestLeft[FindMilestoneSummaryResp]
        }
    }
  }

  val findByMilestoneId = milestoneSummaryEndpoints.findByMilestoneIdEndpoint.serverLogic { auth => request =>
    val (milestoneId, pageRequest) = request
    withProjectMember(milestoneService.findProjectId(milestoneId).map(_.asRight), auth) {
      PageRequestValidation.withPage(pageRequest) { page =>
        milestoneSummaryService
          .findPaginatedByMilestoneId(milestoneId, page)
          .map(summariesPage =>
            TgJsonSuccess.justBodyRight(summariesPage.mapItem(m => toDto(m.milestoneSummary, m.versions.head)))
          )
      }
    }
  }

  val findByProjectId = milestoneSummaryEndpoints.findByProjectIdEndpoint.serverLogic { auth => request =>
    val (projectId, pageRequest) = request
    withProjectMember(IO.pure(projectId.asRight), auth) {
      PageRequestValidation.withPage(pageRequest) { page =>
        milestoneSummaryService
          .findPaginatedByProjectId(projectId, page)
          .map(summariesPage =>
            TgJsonSuccess.justBodyRight(summariesPage.mapItem(m => toDto(m.milestoneSummary, m.versions.head)))
          )
      }
    }
  }

  val update = milestoneSummaryEndpoints.updateEndpoint.serverLogic { auth => dto =>
    withProjectMember[FindMilestoneSummaryResp](projectIdOfSummary(dto.id), auth) {
      validateDates(dto.startDate, dto.endDate) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[FindMilestoneSummaryResp])
        case Right(_) =>
          val now = java.time.Instant.now()
          val newVersion = MilestoneSummaryVersion(
            version = UUID.randomUUID(),
            milestoneSummaryId = dto.id,
            summary = dto.summary,
            startDate = dto.startDate,
            endDate = dto.endDate,
            createdAt = now,
            updatedAt = now
          )

          milestoneSummaryService
            .saveNewVersion(dto.id, newVersion)
            .map {
              case Right(result) => TgJsonSuccess.justBodyRight(toDto(result.milestoneSummary, result.version))
              case Left(error)   => error.toBadRequestLeft[FindMilestoneSummaryResp]
            }
      }
    }
  }

  val delete = milestoneSummaryEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    withProjectMember[TgJsonSuccess.TgEmptyBody](projectIdOfSummary(id), auth) {
      milestoneSummaryService
        .deleteMilestoneSummary(id)
        .map(_ => TgJsonSuccess.justMessageRight("Milestone summary deleted successfully"))
    }
  }

  val listVersions = milestoneSummaryEndpoints.listVersionsEndpoint.serverLogic { auth => request =>
    val (milestoneSummaryId, pageRequest) = request
    withProjectMember(projectIdOfSummary(milestoneSummaryId), auth) {
      PageRequestValidation.withPage(pageRequest) { page =>
        milestoneSummaryService
          .listVersions(milestoneSummaryId, page)
          .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
      }
    }
  }

  val updateVersion = milestoneSummaryEndpoints.updateVersionEndpoint.serverLogic { auth => dto =>
    withProjectMember[MilestoneSummaryVersionDto](projectIdOfSummary(dto.milestoneSummaryId), auth) {
      validateDates(dto.startDate, dto.endDate) match {
        case Left(errors) => IO.pure(errors.toBadRequestLeft[MilestoneSummaryVersionDto])
        case Right(_) =>
          milestoneSummaryService
            .updateVersionContent(dto.version, dto.summary, dto.startDate, dto.endDate)
            .map {
              case Right(version) => TgJsonSuccess.justBodyRight(toVersionDto(version))
              case Left(error)    => error.toBadRequestLeft[MilestoneSummaryVersionDto]
            }
      }
    }
  }

  val deleteVersion = milestoneSummaryEndpoints.deleteVersionEndpoint.serverLogic { auth => version =>
    val projectId = milestoneSummaryService.findMilestoneIdByVersion(version).flatMap(toProjectId)
    withProjectMember[TgJsonSuccess.TgEmptyBody](projectId, auth) {
      milestoneSummaryService
        .deleteVersion(version)
        .map {
          case Right(_)    => TgJsonSuccess.justMessageRight("Milestone summary version deleted successfully")
          case Left(error) => error.toBadRequestLeft[TgJsonSuccess.TgEmptyBody]
        }
    }
  }

  // The summary of a period is not tied to the project's work days (a month ends on any day of the week).
  private def validateDates(startDate: LocalDate, endDate: LocalDate) =
    dateRangeValidation.validateOnOrAfterStart("End date", endDate, startDate).toEither.void

  private def projectIdOfSummary(milestoneSummaryId: UUID): IO[Either[TgError, UUID]] =
    milestoneSummaryService.findMilestoneId(milestoneSummaryId).flatMap(toProjectId)

  private def toProjectId(milestoneId: Either[TgError, UUID]): IO[Either[TgError, UUID]] =
    milestoneId.traverse(milestoneService.findProjectId)

  // Only the members of the project may read or change its summaries.
  private def withProjectMember[T](projectId: IO[Either[TgError, UUID]], auth: TgAuth)(
    f: => IO[Either[TgJsonFailure, TgJsonSuccess[T]]]
  ): IO[Either[TgJsonFailure, TgJsonSuccess[T]]] =
    projectId.flatMap {
      case Left(error) => IO.pure(error.toBadRequestLeft[T])
      case Right(id) =>
        contributionService.isMember(id, auth.body.get.id).flatMap {
          case false => IO.pure(Left(Forbidden))
          case true  => f
        }
    }
}
