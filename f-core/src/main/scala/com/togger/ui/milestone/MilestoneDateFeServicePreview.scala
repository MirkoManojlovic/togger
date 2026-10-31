package com.togger.ui.milestone

import cats.effect.IO
import com.togger.common.DateRangeValidation
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{CreateMilestoneDateReq, FindMilestoneDateResp, UpdateMilestoneDateReq}
import com.togger.preview._

import java.time.{Instant, ZoneOffset}
import java.util.UUID

class MilestoneDateFeServicePreview(
  ctx: PreviewContext,
  data: MilestonesPreviewData,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends MilestoneDateFeService {

  override def createMilestoneDate(dto: CreateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.milestones.find(dto.milestoneId) match {
        case None => PreviewResponses.badRequest[FindMilestoneDateResp]("Milestone not found")
        case Some(milestone) =>
          validated(dto.date, milestone) {
            val now = ctx.clock.currentInstant
            val created = data.dates.upsert(
              FindMilestoneDateResp(
                UUID.randomUUID(),
                dto.milestoneId,
                now,
                UUID.randomUUID(),
                dto.name,
                dto.description,
                dto.date,
                now
              )
            )
            PreviewResponses.ok(created)
          }
      })
    )

  override def findByMilestoneId(
    milestoneId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(sorted(data.dates.filter(_.milestoneId == milestoneId)), page))
    )

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(
        PreviewResponses.paged(sorted(data.dates.filter(d => data.projectIdOf(d.milestoneId).contains(projectId))), page)
      )
    )

  override def updateMilestoneDate(dto: UpdateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(data.dates.find(dto.id) match {
        case None => PreviewResponses.badRequest[FindMilestoneDateResp]("Milestone date not found")
        case Some(current) =>
          data.milestones.find(current.milestoneId) match {
            case None => PreviewResponses.badRequest[FindMilestoneDateResp]("Milestone not found")
            case Some(milestone) =>
              validated(dto.date, milestone) {
                val updated = data.dates.upsert(
                  current.copy(
                    latestVersion = UUID.randomUUID(),
                    name = dto.name,
                    description = dto.description,
                    date = dto.date,
                    updatedAt = ctx.clock.currentInstant
                  )
                )
                PreviewResponses.ok(updated)
              }
          }
      })
    )

  private def validated[A](date: Instant, milestone: PreviewMilestone)(onValid: => TgRespIO[A]): TgRespIO[A] =
    dateRangeValidation
      .validateOnOrAfterStart("Date", date.atOffset(ZoneOffset.UTC).toLocalDate, milestone.startDate)
      .toEither match {
      case Left(errors) => errors.toBadRequestLeftIO[A]
      case Right(_)     => onValid
    }

  private def sorted(dates: List[FindMilestoneDateResp]): List[FindMilestoneDateResp] =
    dates.sortBy(d => (d.date.toEpochMilli, d.id.toString))
}
