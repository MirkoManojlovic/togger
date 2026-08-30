package com.togger.ui.projectmapping

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp}
import com.togger.projectmapping.ProjectMappingEndpoints

import java.util.UUID

trait ProjectMappingFeService {
  def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp]

  def findByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]]

  def findByProjectIdDump(projectIdDump: UUID): TgRespIO[Seq[ProjectMappingResp]]

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): TgRespIO[TgEmptyBody]
}

class ProjectMappingFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectMappingFeService {

  private val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)

  override def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.saveEndpoint)(dto)

  override def findByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByProjectIdDbEndpoint)(projectIdDb)

  override def findByProjectIdDump(projectIdDump: UUID): TgRespIO[Seq[ProjectMappingResp]] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByProjectIdDumpEndpoint)(projectIdDump)

  override def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.deleteEndpoint)((projectIdDb, projectIdDump))
}
