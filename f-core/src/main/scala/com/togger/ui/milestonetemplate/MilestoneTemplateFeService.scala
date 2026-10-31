package com.togger.ui.milestonetemplate

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.milestone.MilestoneTemplateEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait MilestoneTemplateFeService {
  def createMilestoneTemplate(dto: CreateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp]

  def updateMilestoneTemplate(dto: UpdateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindMilestoneTemplateResp]]

  /** Every milestone template of the project, for the create milestone form; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindMilestoneTemplateResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findMilestoneTemplateById(id: UUID): TgRespIO[FindMilestoneTemplateResp]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneTemplateVersionDto]]

  /** Every version of the template, for the read-only history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[MilestoneTemplateVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteMilestoneTemplateById(id: UUID): TgRespIO[TgEmptyBody]
}

class MilestoneTemplateFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends MilestoneTemplateFeService {
  private val endpoints = new MilestoneTemplateEndpoints(securedEndpoint)

  override def createMilestoneTemplate(dto: CreateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.saveEndpoint)(dto)

  override def updateMilestoneTemplate(dto: UpdateMilestoneTemplateReq): TgRespIO[FindMilestoneTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.updateEndpoint)(dto)

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindMilestoneTemplateResp]] =
    protectedBackendClient.protectedHttpCall(endpoints.findByProjectIdEndpoint)((projectId, page))

  override def findMilestoneTemplateById(id: UUID): TgRespIO[FindMilestoneTemplateResp] =
    protectedBackendClient.protectedHttpCall(endpoints.findByIdEndpoint)(id)

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[MilestoneTemplateVersionDto]] =
    protectedBackendClient.protectedHttpCall(endpoints.listVersionsEndpoint)((id, page))

  override def deleteMilestoneTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(endpoints.deleteByIdEndpoint)(id)
}
