package com.togger.ui.milestonesteptemplate

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.PageRequest
import com.togger.common.PaginatedResponse
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto._
import com.togger.milestone.MilestoneStepTemplateEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait MilestoneStepTemplateFeService {
  def createMilestoneStepTemplate(dto: CreateMilestoneStepTemplateReq): TgRespIO[FindMilestoneStepTemplateResp]

  def updateMilestoneStepTemplate(dto: UpdateMilestoneStepTemplateReq): TgRespIO[FindMilestoneStepTemplateResp]

  def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneStepTemplateResp]]

  /** Every step template of the project, for the milestone template forms; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneStepTemplateResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findMilestoneStepTemplateById(id: UUID): TgRespIO[FindMilestoneStepTemplateResp]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneStepTemplateVersionDto]]

  /** Every version of the template, for the read-only history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[MilestoneStepTemplateVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteMilestoneStepTemplateById(id: UUID): TgRespIO[TgEmptyBody]
}

class MilestoneStepTemplateFeServiceImpl(
  securedEndpoint: SecuredEndpoint,
  protectedBackendClient: ProtectedBackendClient
) extends MilestoneStepTemplateFeService {
  private val endpoints = new MilestoneStepTemplateEndpoints(securedEndpoint)

  override def createMilestoneStepTemplate(
    dto: CreateMilestoneStepTemplateReq
  ): TgRespIO[FindMilestoneStepTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.saveEndpoint)(dto)

  override def updateMilestoneStepTemplate(
    dto: UpdateMilestoneStepTemplateReq
  ): TgRespIO[FindMilestoneStepTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.updateEndpoint)(dto)

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneStepTemplateResp]] =
    protectedBackendClient.protectedHttpCall(endpoints.findByProjectIdEndpoint)((projectId, page))

  override def findMilestoneStepTemplateById(id: UUID): TgRespIO[FindMilestoneStepTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.findByIdEndpoint)(id)

  override def listVersions(
    id: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[MilestoneStepTemplateVersionDto]] =
    protectedBackendClient.protectedHttpCall(endpoints.listVersionsEndpoint)((id, page))

  override def deleteMilestoneStepTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(endpoints.deleteByIdEndpoint)(id)
}
