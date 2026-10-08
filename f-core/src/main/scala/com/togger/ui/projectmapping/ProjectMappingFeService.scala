package com.togger.ui.projectmapping

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp}
import com.togger.projectmapping.ProjectMappingEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait ProjectMappingFeService {
  def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp]

  def findByProjectIdDb(projectIdDb: UUID, page: PageRequest): TgRespIO[PaginatedResponse[ProjectMappingResp]]

  /** Every mapping of the project: it has one per dump it was loaded from; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]]

  def findByProjectIdDump(projectIdDump: UUID, page: PageRequest): TgRespIO[PaginatedResponse[ProjectMappingResp]]

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): TgRespIO[TgEmptyBody]
}

class ProjectMappingFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectMappingFeService {

  private val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)

  override def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.saveEndpoint)(dto)

  override def findByProjectIdDb(
    projectIdDb: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[ProjectMappingResp]] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByProjectIdDbEndpoint)((projectIdDb, page))

  override def findAllByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]] =
    PaginatedLoader.loadAll(findByProjectIdDb(projectIdDb, _))

  override def findByProjectIdDump(
    projectIdDump: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[ProjectMappingResp]] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByProjectIdDumpEndpoint)(
      (projectIdDump, page)
    )

  override def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.deleteEndpoint)((projectIdDb, projectIdDump))
}
