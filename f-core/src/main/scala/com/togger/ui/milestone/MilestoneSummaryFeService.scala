package com.togger.ui.milestone

import cats.effect.IO
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{
  CreateMilestoneSummaryReq,
  FindMilestoneSummaryResp,
  SummariseMilestoneWithAiReq,
  UpdateMilestoneSummaryReq
}
import com.togger.milestone.MilestoneSummaryEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class MilestoneSummaryFeService(
  securedEndpoint: SecuredEndpoint,
  protectedBackendClient: ProtectedBackendClient,
  aiSummaryStreamClient: MilestoneAiSummaryStreamClient
) {
  val milestoneSummaryEndpoints = new MilestoneSummaryEndpoints(securedEndpoint)

  def createMilestoneSummary(dto: CreateMilestoneSummaryReq): TgRespIO[FindMilestoneSummaryResp] =
    protectedBackendClient.protectedHttpCall(milestoneSummaryEndpoints.saveEndpoint)(dto)

  /** Streams the summary as the AI writes it; see [[MilestoneAiSummaryStreamClient.stream]] for the events. */
  def summariseWithAi(dto: SummariseMilestoneWithAiReq)(onEvent: AiSummaryEvent => Unit): IO[Unit] =
    aiSummaryStreamClient.stream(dto)(onEvent)

  def findByMilestoneId(milestoneId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneSummaryResp]] =
    protectedBackendClient.protectedHttpCall(milestoneSummaryEndpoints.findByMilestoneIdEndpoint)((milestoneId, page))

  /** Every summary of the milestone, for the pages that show all of them; see [[PaginatedLoader]] for the bound. */
  def findAllByMilestoneId(milestoneId: UUID): TgRespIO[Seq[FindMilestoneSummaryResp]] =
    PaginatedLoader.loadAll(findByMilestoneId(milestoneId, _))

  def updateMilestoneSummary(dto: UpdateMilestoneSummaryReq): TgRespIO[FindMilestoneSummaryResp] =
    protectedBackendClient.protectedHttpCall(milestoneSummaryEndpoints.updateEndpoint)(dto)

  def deleteMilestoneSummary(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(milestoneSummaryEndpoints.deleteByIdEndpoint)(id)
}
