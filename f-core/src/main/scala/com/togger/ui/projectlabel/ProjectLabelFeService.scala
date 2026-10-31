package com.togger.ui.projectlabel

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.projectlabel.ProjectLabelEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait ProjectLabelFeService {
  def createProjectLabel(dto: CreateProjectLabelReq): TgRespIO[CreateProjectLabelResp]

  def updateProjectLabel(dto: UpdateProjectLabelResp): TgRespIO[FindProjectLabelResp]

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelResp]]

  /** Every project label, for the pickers offering all of them; see [[PaginatedLoader]] for the bound. */
  def findEvery(): TgRespIO[Seq[FindProjectLabelResp]] =
    PaginatedLoader.loadAll(findAll)

  def findProjectLabelById(id: UUID): TgRespIO[FindProjectLabelResp]

  def deleteProjectLabelById(id: UUID): TgRespIO[TgEmptyBody]
}

class ProjectLabelFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectLabelFeService {
  val projectLabelEndpoints = new ProjectLabelEndpoints(securedEndpoint)

  override def createProjectLabel(dto: CreateProjectLabelReq): TgRespIO[CreateProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.saveEndpoint)(dto)

  override def updateProjectLabel(dto: UpdateProjectLabelResp): TgRespIO[FindProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.updateEndpoint)(dto)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelResp]] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.findAllEndpoint)(page)

  override def findProjectLabelById(id: UUID): TgRespIO[FindProjectLabelResp] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.findByIdEndpoint)(id)

  override def deleteProjectLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectLabelEndpoints.deleteByIdEndpoint)(id)
}
