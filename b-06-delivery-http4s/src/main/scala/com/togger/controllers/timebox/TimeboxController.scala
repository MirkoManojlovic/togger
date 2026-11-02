package com.togger.controllers.timebox

import cats.data.NonEmptyChain
import cats.data.ValidatedNec
import cats.effect.IO
import cats.implicits._
import com.togger.common.DateRangeValidation
import com.togger.common.TgClock
import com.togger.common.TgError
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.timebox.TimeboxDtoMapper.toDto
import com.togger.controllers.timebox.TimeboxDtoMapper.toVersionDto
import com.togger.dto.FindTimeboxResp
import com.togger.dto.SaveTimeboxVersionReq
import com.togger.timebox.TimeboxEndpoints
import com.togger.timebox.TimeboxError.ChangeCommentEmpty
import com.togger.timebox.TimeboxService

import java.time.LocalDate

class TimeboxController(
  timeboxEndpoints: TimeboxEndpoints,
  timeboxService: TimeboxService,
  dateRangeValidation: DateRangeValidation,
  tgClock: TgClock
) {

  private def validateRange(start: LocalDate, end: LocalDate): Either[NonEmptyChain[TgError], Unit] = {
    val validated: ValidatedNec[TgError, LocalDate] = dateRangeValidation.validateOnOrAfterStart("End date", end, start)
    validated.toEither.map(_ => ())
  }

  private def validateChangeComment(comment: String): Either[NonEmptyChain[TgError], Unit] =
    Either.cond(comment.trim.nonEmpty, (), NonEmptyChain.one[TgError](ChangeCommentEmpty))

  private def validateNewVersion(dto: SaveTimeboxVersionReq): Either[NonEmptyChain[TgError], Unit] =
    validateRange(dto.startDate, dto.endDate).toValidated
      .combine(validateChangeComment(dto.changeComment).toValidated)
      .toEither

  val save = timeboxEndpoints.saveEndpoint.serverLogic { _ => dto =>
    validateRange(dto.startDate, dto.endDate) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTimeboxResp])
      case Right(_) =>
        timeboxService
          .createTimebox(
            dto.projectId,
            dto.startDate,
            dto.endDate,
            dto.description,
            dto.taskIds,
            tgClock.currentInstant
          )
          .map {
            case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
            case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
          }
    }
  }

  val saveNewVersion = timeboxEndpoints.saveNewVersionEndpoint.serverLogic { _ => dto =>
    validateNewVersion(dto) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTimeboxResp])
      case Right(_) =>
        timeboxService
          .saveNewVersion(
            dto.timeboxId,
            dto.startDate,
            dto.endDate,
            dto.description,
            dto.changeComment.trim,
            dto.taskIds,
            tgClock.currentInstant
          )
          .map {
            case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
            case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
          }
    }
  }

  val findById = timeboxEndpoints.findByIdEndpoint.serverLogic { _ => id =>
    timeboxService.getCurrent(id).map {
      case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
      case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
    }
  }

  val findByProjectId = timeboxEndpoints.findByProjectIdEndpoint.serverLogic { _ => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .findPaginatedByProjectId(projectId, page)
        .map(timeboxesPage => TgJsonSuccess.justBodyRight(timeboxesPage.mapItem(toDto)))
    }
  }

  val listVersions = timeboxEndpoints.listVersionsEndpoint.serverLogic { _ => request =>
    val (timeboxId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .listVersions(timeboxId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val delete = timeboxEndpoints.deleteByIdEndpoint.serverLogic { _ => id =>
    timeboxService.deleteTimebox(id).map(_ => TgJsonSuccess.justMessageRight("Timebox deleted successfully"))
  }

}
