package com.togger.ui.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{CreateMilestoneReq, FindMilestoneResp, MilestoneFilter, UpdateMilestoneReq}
import com.togger.milestone.MilestoneEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait MilestoneFeService {
  def createMilestone(dto: CreateMilestoneReq): TgRespIO[FindMilestoneResp]

  def findMilestoneById(id: UUID): TgRespIO[FindMilestoneResp]

  def updateMilestone(dto: UpdateMilestoneReq): TgRespIO[FindMilestoneResp]

  def findByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneResp]]

  /** Every milestone of the project, for the pickers, filters and charts offering all of them; see [[PaginatedLoader]]
    * for the bound.
    */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, MilestoneFilter.empty, _))
}

class MilestoneFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends MilestoneFeService {
  val milestoneEndpoints = new MilestoneEndpoints(securedEndpoint)

  override def createMilestone(dto: CreateMilestoneReq): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.saveEndpoint)(dto)

  override def findMilestoneById(id: UUID): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.findByIdEndpoint)(id)

  override def updateMilestone(dto: UpdateMilestoneReq): TgRespIO[FindMilestoneResp] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.updateEndpoint)(dto)

  override def findByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneResp]] =
    protectedBackendClient.protectedHttpCall(milestoneEndpoints.findByProjectIdEndpoint)((projectId, filter, page))
}
