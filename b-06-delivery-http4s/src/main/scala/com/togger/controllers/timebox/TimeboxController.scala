package com.togger.controllers.timebox

import cats.effect.IO
import cats.data.{NonEmptyChain, ValidatedNec}
import com.togger.common.TgError._
import com.togger.common.{DateRangeValidation, TgClock, TgError, TgJsonSuccess}
import com.togger.controllers.common.PageRequestValidation
import com.togger.controllers.timebox.TimeboxDtoMapper.{toDto, toVersionDto}
import com.togger.dto.FindTimeboxResp
import com.togger.timebox.{TimeboxEndpoints, TimeboxService}

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

  val save = timeboxEndpoints.saveEndpoint.serverLogic { auth => dto =>
    validateRange(dto.startDate, dto.endDate) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTimeboxResp])
      case Right(_) =>
        timeboxService
          .createTimebox(dto.projectId, dto.startDate, dto.endDate, dto.description, dto.taskIds, tgClock.currentInstant)
          .map {
            case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
            case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
          }
    }
  }

  val saveNewVersion = timeboxEndpoints.saveNewVersionEndpoint.serverLogic { auth => dto =>
    validateRange(dto.startDate, dto.endDate) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTimeboxResp])
      case Right(_) =>
        timeboxService
          .saveNewVersion(dto.timeboxId, dto.startDate, dto.endDate, dto.description, dto.taskIds, tgClock.currentInstant)
          .map {
            case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
            case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
          }
    }
  }

  val findById = timeboxEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    timeboxService.getCurrent(id).map {
      case Right(result) => TgJsonSuccess.justBodyRight(toDto(result))
      case Left(error)   => error.toBadRequestLeft[FindTimeboxResp]
    }
  }

  val findByProjectId = timeboxEndpoints.findByProjectIdEndpoint.serverLogic { auth => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .findPaginatedByProjectId(projectId, page)
        .map(timeboxesPage => TgJsonSuccess.justBodyRight(timeboxesPage.mapItem(toDto)))
    }
  }

  val listVersions = timeboxEndpoints.listVersionsEndpoint.serverLogic { auth => request =>
    val (timeboxId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .listVersions(timeboxId, page)
        .map(versionsPage => TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto)))
    }
  }

  val delete = timeboxEndpoints.deleteByIdEndpoint.serverLogic { auth => id =>
    timeboxService.deleteTimebox(id).map(_ => TgJsonSuccess.justMessageRight("Timebox deleted successfully"))
  }

}
