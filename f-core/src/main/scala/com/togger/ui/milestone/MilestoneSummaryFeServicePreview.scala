package com.togger.ui.milestone

import cats.effect.IO
import com.togger.common.TgError._
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{DateRangeValidation, Forbidden, PageRequest, PaginatedResponse}
import com.togger.dto.{
  CreateMilestoneSummaryReq,
  FindMilestoneSummaryResp,
  SummariseMilestoneWithAiReq,
  UpdateMilestoneSummaryReq
}
import com.togger.preview._

import java.time.LocalDate
import java.util.UUID

/** `aiSummaryStreamClient` is the preview one in the app; summaries need project membership like in the backend. */
class MilestoneSummaryFeServicePreview(
  ctx: PreviewContext,
  projects: ProjectsPreviewData,
  data: MilestonesPreviewData,
  aiSummaryStreamClient: MilestoneAiSummaryStreamClient,
  dateRangeValidation: DateRangeValidation = new DateRangeValidation
) extends MilestoneSummaryFeService {

  private val MilestoneNotFound = "Milestone not found"
  private val SummaryNotFound = "Milestone summary not found"

  override def createMilestoneSummary(dto: CreateMilestoneSummaryReq): TgRespIO[FindMilestoneSummaryResp] =
    asProjectMember(data.projectIdOf(dto.milestoneId), MilestoneNotFound) {
      validDates(dto.startDate, dto.endDate) {
        val now = ctx.clock.currentInstant
        PreviewResponses.ok(
          data.summaries.upsert(
            FindMilestoneSummaryResp(
              UUID.randomUUID(),
              dto.milestoneId,
              now,
              UUID.randomUUID(),
              dto.summary,
              dto.startDate,
              dto.endDate,
              now
            )
          )
        )
      }
    }

  override def summariseWithAi(dto: SummariseMilestoneWithAiReq)(onEvent: AiSummaryEvent => Unit): IO[Unit] =
    aiSummaryStreamClient.stream(dto)(onEvent)

  override def findByMilestoneId(
    milestoneId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneSummaryResp]] =
    asProjectMember(data.projectIdOf(milestoneId), MilestoneNotFound) {
      PreviewResponses.paged(
        data.summaries
          .filter(_.milestoneId == milestoneId)
          .sortBy(s => (s.createdAt.toEpochMilli, s.id.toString)),
        page
      )
    }

  override def updateMilestoneSummary(dto: UpdateMilestoneSummaryReq): TgRespIO[FindMilestoneSummaryResp] =
    asProjectMember(projectIdOfSummary(dto.id), SummaryNotFound) {
      validDates(dto.startDate, dto.endDate) {
        PreviewResponses.fromOption(
          data.summaries.update(dto.id)(
            _.copy(
              latestVersion = UUID.randomUUID(),
              summary = dto.summary,
              startDate = dto.startDate,
              endDate = dto.endDate,
              updatedAt = ctx.clock.currentInstant
            )
          )
        )
      }
    }

  override def deleteMilestoneSummary(id: UUID): TgRespIO[TgEmptyBody] =
    asProjectMember(projectIdOfSummary(id), SummaryNotFound) {
      data.summaries.delete(id)
      PreviewResponses.okEmpty("Milestone summary deleted successfully")
    }

  private def projectIdOfSummary(summaryId: UUID): Option[UUID] =
    data.summaries.find(summaryId).flatMap(summary => data.projectIdOf(summary.milestoneId))

  private def asProjectMember[A](projectId: => Option[UUID], notFound: String)(onMember: => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo)(callerId =>
      IO.defer(projectId match {
        case None                                           => PreviewResponses.badRequest[A](notFound)
        case Some(id) if !projects.isMember(id, callerId) => IO.pure(Left(Forbidden))
        case Some(_)                                        => onMember
      })
    )

  private def validDates[A](startDate: LocalDate, endDate: LocalDate)(onValid: => TgRespIO[A]): TgRespIO[A] =
    dateRangeValidation.validateOnOrAfterStart("End date", endDate, startDate).toEither match {
      case Left(errors) => errors.toBadRequestLeftIO[A]
      case Right(_)     => onValid
    }
}
