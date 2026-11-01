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
import com.togger.milestone.MilestoneStepService
import com.togger.timebox.TimeboxError.TimeboxError
import com.togger.timebox.TimeboxVersionWithTasks
import com.togger.timebox.TimeboxWithOneVersion

import java.time.LocalDate
import java.util.UUID

class TimeboxController(
  timeboxEndpoints: TimeboxEndpoints,
  timeboxService: TimeboxService,
  dateRangeValidation: DateRangeValidation,
  tgClock: TgClock,
  milestoneStepService: MilestoneStepService
) {

  /** The milestone of the steps the planned tasks are in, as the milestone of a task is the one of its step. */
  private def milestonesOf(versions: Iterable[TimeboxVersionWithTasks]): IO[UUID => Option[UUID]] =
    milestoneStepService
      .findMilestoneIdsByStepIds(versions.flatMap(_.tasks.flatMap(_.milestoneStepId)))
      .map(milestones => (id: UUID) => milestones.get(id))

  private def respond(result: Either[TimeboxError, TimeboxWithOneVersion]) =
    result match {
      case Right(found) =>
        milestonesOf(List(found.version)).map(milestoneOf => TgJsonSuccess.justBodyRight(toDto(found, milestoneOf)))
      case Left(error) => IO.pure(error.toBadRequestLeft[FindTimeboxResp])
    }

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
          .createTimebox(dto.projectId, dto.startDate, dto.endDate, dto.description, dto.taskIds, tgClock.currentInstant)
          .flatMap(respond)
    }
  }

  val saveNewVersion = timeboxEndpoints.saveNewVersionEndpoint.serverLogic { _ => dto =>
    validateNewVersion(dto) match {
      case Left(errors) => IO.pure(errors.toBadRequestLeft[FindTimeboxResp])
      case Right(_) =>
        timeboxService
          .saveNewVersion(dto.timeboxId, dto.startDate, dto.endDate, dto.description, dto.changeComment.trim, dto.taskIds, tgClock.currentInstant)
          .flatMap(respond)
    }
  }

  val findById = timeboxEndpoints.findByIdEndpoint.serverLogic { auth => id =>
    timeboxService.getCurrent(id).flatMap(respond)
  }

  val findByProjectId = timeboxEndpoints.findByProjectIdEndpoint.serverLogic { _ => request =>
    val (projectId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .findPaginatedByProjectId(projectId, page)
        .flatMap(timeboxesPage =>
          milestonesOf(timeboxesPage.items.map(_.version)).map(milestoneOf =>
            TgJsonSuccess.justBodyRight(timeboxesPage.mapItem(toDto(_, milestoneOf)))
          )
        )
    }
  }

  val listVersions = timeboxEndpoints.listVersionsEndpoint.serverLogic { _ => request =>
    val (timeboxId, pageRequest) = request
    PageRequestValidation.withPage(pageRequest) { page =>
      timeboxService
        .listVersions(timeboxId, page)
        .flatMap(versionsPage =>
          milestonesOf(versionsPage.items).map(milestoneOf =>
            TgJsonSuccess.justBodyRight(versionsPage.mapItem(toVersionDto(_, milestoneOf)))
          )
        )
    }
  }

  val delete = timeboxEndpoints.deleteByIdEndpoint.serverLogic { _ => id =>
    timeboxService.deleteTimebox(id).map(_ => TgJsonSuccess.justMessageRight("Timebox deleted successfully"))
  }

}
