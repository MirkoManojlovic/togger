package com.togger.ui.milestone

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto._
import com.togger.milestone.MilestoneStepEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait MilestoneStepFeService {
  def createMilestoneStep(dto: CreateMilestoneStepReq): TgRespIO[FindMilestoneStepResp]

  /** Saves a new version; the change comment is mandatory. */
  def updateMilestoneStep(dto: UpdateMilestoneStepReq): TgRespIO[FindMilestoneStepResp]

  /** Gives the steps of the milestone the order of `stepIds`; the change comment is mandatory. */
  def reorderMilestoneSteps(dto: ReorderMilestoneStepsReq): TgRespIO[List[FindMilestoneStepResp]]

  /** All steps of the milestone ordered by position; a milestone has a short list of them, so it is not paged. */
  def findByMilestoneId(milestoneId: UUID): TgRespIO[List[FindMilestoneStepResp]]

  /** The steps of every milestone of the project by milestone then position, for the pickers of the task forms. */
  def findByProjectId(projectId: UUID): TgRespIO[List[FindMilestoneStepResp]]

  def findMilestoneStepById(id: UUID): TgRespIO[FindMilestoneStepResp]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneStepVersionDto]]

  /** Every version of the step, for the history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[MilestoneStepVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteMilestoneStepById(id: UUID): TgRespIO[TgEmptyBody]
}

class MilestoneStepFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends MilestoneStepFeService {
  private val endpoints = new MilestoneStepEndpoints(securedEndpoint)

  override def createMilestoneStep(dto: CreateMilestoneStepReq): TgRespIO[FindMilestoneStepResp] =
    protectedBackendClient.protectedHttpCall(endpoints.saveEndpoint)(dto)

  override def updateMilestoneStep(dto: UpdateMilestoneStepReq): TgRespIO[FindMilestoneStepResp] =
    protectedBackendClient.protectedHttpCall(endpoints.updateEndpoint)(dto)

  override def reorderMilestoneSteps(dto: ReorderMilestoneStepsReq): TgRespIO[List[FindMilestoneStepResp]] =
    protectedBackendClient.protectedHttpCall(endpoints.reorderEndpoint)(dto)

  override def findByMilestoneId(milestoneId: UUID): TgRespIO[List[FindMilestoneStepResp]] =
    protectedBackendClient.protectedHttpCall(endpoints.findByMilestoneIdEndpoint)(milestoneId)

  override def findByProjectId(projectId: UUID): TgRespIO[List[FindMilestoneStepResp]] =
    protectedBackendClient.protectedHttpCall(endpoints.findByProjectIdEndpoint)(projectId)

  override def findMilestoneStepById(id: UUID): TgRespIO[FindMilestoneStepResp] =
    protectedBackendClient.protectedHttpCall(endpoints.findByIdEndpoint)(id)

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneStepVersionDto]] =
    protectedBackendClient.protectedHttpCall(endpoints.listVersionsEndpoint)((id, page))

  override def deleteMilestoneStepById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(endpoints.deleteByIdEndpoint)(id)
}
