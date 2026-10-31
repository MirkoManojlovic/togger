package com.togger.ui.timebox

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{DateRangeValidation, PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.preview._

import java.time.LocalDate
import java.util.UUID

class TimeboxFeServicePreview(
  ctx: PreviewContext,
  data: TasksPreviewData,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends TimeboxFeService {

  private val TimeboxNotFound = "Timebox not found"
  private val Overlaps = "Timebox dates must not overlap with another timebox of the project"

  private def secured[A](response: => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ => IO.defer(response))

  override def createTimebox(dto: CreateTimeboxReq): TgRespIO[FindTimeboxResp] =
    secured {
      validateRange(dto.startDate, dto.endDate) match {
        case Some(error) => error
        case None if overlaps(dto.projectId, dto.startDate, dto.endDate, None) =>
          PreviewResponses.badRequest[FindTimeboxResp](Overlaps)
        case None =>
          resolveTaskVersions(dto.projectId, dto.taskIds).fold(
            PreviewResponses.badRequest[FindTimeboxResp](_),
            versionIds => {
              val timebox = data.timeboxes.upsert(PreviewTimebox(UUID.randomUUID(), dto.projectId, ctx.clock.currentInstant))
              val version = insertVersion(timebox.id, dto.startDate, dto.endDate, dto.description, "", versionIds)
              PreviewResponses.ok(toDto(timebox, version))
            }
          )
      }
    }

  override def saveNewVersion(dto: SaveTimeboxVersionReq): TgRespIO[FindTimeboxResp] =
    secured {
      val rangeError = validateRange(dto.startDate, dto.endDate)
      val commentError =
        Option.when(dto.changeComment.trim.isEmpty)(PreviewResponses.badRequest[FindTimeboxResp]("Change comment must not be empty"))
      (rangeError.orElse(commentError), data.timeboxes.find(dto.timeboxId)) match {
        case (Some(error), _) => error
        case (None, None)     => PreviewResponses.badRequest[FindTimeboxResp](TimeboxNotFound)
        case (None, Some(timebox)) if overlaps(timebox.projectId, dto.startDate, dto.endDate, Some(timebox.id)) =>
          PreviewResponses.badRequest[FindTimeboxResp](Overlaps)
        case (None, Some(timebox)) =>
          resolveTaskVersions(timebox.projectId, dto.taskIds).fold(
            PreviewResponses.badRequest[FindTimeboxResp](_),
            versionIds => {
              val version =
                insertVersion(timebox.id, dto.startDate, dto.endDate, dto.description, dto.changeComment.trim, versionIds)
              PreviewResponses.ok(toDto(timebox, version))
            }
          )
      }
    }

  override def findById(id: UUID): TgRespIO[FindTimeboxResp] =
    secured {
      data.timeboxes
        .find(id)
        .zip(data.latestTimeboxVersion(id))
        .fold(PreviewResponses.badRequest[FindTimeboxResp](TimeboxNotFound)) { case (timebox, version) =>
          PreviewResponses.ok(toDto(timebox, version))
        }
    }

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTimeboxResp]] =
    secured {
      val items = data.timeboxes
        .filter(_.projectId == projectId)
        .flatMap(timebox => data.latestTimeboxVersion(timebox.id).map(timebox -> _))
        .sortBy { case (timebox, version) => (-version.startDate.toEpochDay, timebox.id.toString) }
        .map { case (timebox, version) => toDto(timebox, version) }
      PreviewResponses.paged(items, page)
    }

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TimeboxVersionDto]] =
    secured(PreviewResponses.paged(data.timeboxVersions.filter(_.timeboxId == id).reverse.map(toVersionDto), page))

  override def deleteTimebox(id: UUID): TgRespIO[TgEmptyBody] =
    secured {
      data.deleteTimebox(id)
      PreviewResponses.okEmpty("Timebox deleted successfully")
    }

  private def validateRange[A](start: LocalDate, end: LocalDate): Option[TgRespIO[A]] =
    dateRangeValidation.validateOnOrAfterStart("End date", end, start).toEither.left.toOption.map(_.toBadRequestLeftIO[A])

  private def overlaps(projectId: UUID, start: LocalDate, end: LocalDate, excluded: Option[UUID]): Boolean =
    data.timeboxes
      .filter(timebox => timebox.projectId == projectId && !excluded.contains(timebox.id))
      .flatMap(timebox => data.latestTimeboxVersion(timebox.id))
      .exists(other => !start.isAfter(other.endDate) && !end.isBefore(other.startDate))

  private def resolveTaskVersions(projectId: UUID, taskIds: Seq[UUID]): Either[String, List[UUID]] =
    taskIds.distinct.toList.foldLeft[Either[String, List[UUID]]](Right(List.empty)) { (acc, taskId) =>
      acc.flatMap { versions =>
        data.tasks.find(taskId) match {
          case None                                       => Left(s"Task $taskId not found")
          case Some(task) if task.projectId != projectId  => Left(s"Task $taskId does not belong to the project of the timebox")
          case Some(_) =>
            data.latestTaskVersion(taskId).map(v => versions :+ v.version).toRight(s"Task $taskId not found")
        }
      }
    }

  private def insertVersion(
    timeboxId: UUID,
    start: LocalDate,
    end: LocalDate,
    description: Option[String],
    comment: String,
    taskVersionIds: List[UUID]
  ): PreviewTimeboxVersion =
    data.timeboxVersions.upsert(
      PreviewTimeboxVersion(
        UUID.randomUUID(),
        timeboxId,
        start,
        end,
        description,
        ctx.clock.currentInstant,
        comment,
        taskVersionIds
      )
    )

  private def toVersionDto(version: PreviewTimeboxVersion): TimeboxVersionDto =
    TimeboxVersionDto(
      version.version,
      version.timeboxId,
      version.startDate,
      version.endDate,
      version.description,
      version.createdAt,
      version.changeComment,
      version.taskVersionIds.flatMap(data.taskVersions.find)
    )

  private def toDto(timebox: PreviewTimebox, version: PreviewTimeboxVersion): FindTimeboxResp =
    FindTimeboxResp(timebox.id, timebox.projectId, timebox.createdAt, toVersionDto(version))
}
