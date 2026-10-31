package com.togger.ui.projectlabel

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.projectlabel.ProjectLabelCategoryEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait ProjectLabelCategoryFeService {
  def createProjectLabelCategory(dto: CreateProjectLabelCategoryReq): TgRespIO[CreateProjectLabelCategoryResp]

  def updateProjectLabelCategory(dto: UpdateProjectLabelCategoryResp): TgRespIO[FindProjectLabelCategoryResp]

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelCategoryResp]]

  /** Every project label category, for the pickers and name lookups needing all of them; see [[PaginatedLoader]] for
    * the bound.
    */
  def findEvery(): TgRespIO[Seq[FindProjectLabelCategoryResp]] =
    PaginatedLoader.loadAll(findAll)

  def findProjectLabelCategoryById(id: UUID): TgRespIO[FindProjectLabelCategoryResp]

  def deleteProjectLabelCategoryById(id: UUID): TgRespIO[TgEmptyBody]
}

class ProjectLabelCategoryFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectLabelCategoryFeService {
  val projectLabelCategoryEndpoints = new ProjectLabelCategoryEndpoints(securedEndpoint)

  override def createProjectLabelCategory(
    dto: CreateProjectLabelCategoryReq
  ): TgRespIO[CreateProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.saveEndpoint)(dto)

  override def updateProjectLabelCategory(dto: UpdateProjectLabelCategoryResp): TgRespIO[FindProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.updateEndpoint)(dto)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindProjectLabelCategoryResp]] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.findAllEndpoint)(page)

  override def findProjectLabelCategoryById(id: UUID): TgRespIO[FindProjectLabelCategoryResp] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.findByIdEndpoint)(id)

  override def deleteProjectLabelCategoryById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectLabelCategoryEndpoints.deleteByIdEndpoint)(id)
}
