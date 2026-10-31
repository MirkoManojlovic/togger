package com.togger.ui.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{CreateMilestoneDateReq, FindMilestoneDateResp, UpdateMilestoneDateReq}
import com.togger.milestone.MilestoneDateEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait MilestoneDateFeService {
  def createMilestoneDate(dto: CreateMilestoneDateReq): TgRespIO[FindMilestoneDateResp]

  def findByMilestoneId(milestoneId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneDateResp]]

  /** Every date of the milestone, for the form that edits all of them; see [[PaginatedLoader]] for the bound. */
  def findAllByMilestoneId(milestoneId: UUID): TgRespIO[Seq[FindMilestoneDateResp]] =
    PaginatedLoader.loadAll(findByMilestoneId(milestoneId, _))

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneDateResp]]

  /** Every date of the project's milestones, for the history chart; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneDateResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def updateMilestoneDate(dto: UpdateMilestoneDateReq): TgRespIO[FindMilestoneDateResp]
}

class MilestoneDateFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends MilestoneDateFeService {
  val milestoneDateEndpoints = new MilestoneDateEndpoints(securedEndpoint)

  override def createMilestoneDate(dto: CreateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.saveEndpoint)(dto)

  override def findByMilestoneId(
    milestoneId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.findByMilestoneIdEndpoint)((milestoneId, page))

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneDateResp]] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def updateMilestoneDate(dto: UpdateMilestoneDateReq): TgRespIO[FindMilestoneDateResp] =
    protectedBackendClient.protectedHttpCall(milestoneDateEndpoints.updateEndpoint)(dto)
}
