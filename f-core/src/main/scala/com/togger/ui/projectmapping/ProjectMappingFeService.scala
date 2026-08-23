package com.togger.ui.projectmapping

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp, UpdateProjectMappingReq}
import com.togger.projectmapping.ProjectMappingEndpoints

import java.util.UUID

trait ProjectMappingFeService {
  def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp]

  def findById(id: UUID): TgRespIO[ProjectMappingResp]

  def update(dto: UpdateProjectMappingReq): TgRespIO[ProjectMappingResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]]
}

class ProjectMappingFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends ProjectMappingFeService {

  private val projectMappingEndpoints = new ProjectMappingEndpoints(securedEndpoint)

  override def create(dto: CreateProjectMappingReq): TgRespIO[ProjectMappingResp] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.saveEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[ProjectMappingResp] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateProjectMappingReq): TgRespIO[ProjectMappingResp] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.updateEndpoint)(dto)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.deleteByIdEndpoint)(id)

  override def findByProjectIdDb(projectIdDb: UUID): TgRespIO[Seq[ProjectMappingResp]] =
    protectedBackendClient.protectedHttpCall(projectMappingEndpoints.findByProjectIdDbEndpoint)(projectIdDb)
}
