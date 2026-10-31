package com.togger.ui.tasktemplate

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.task.TaskTemplateEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait TaskTemplateFeService {
  def createTaskTemplate(dto: CreateTaskTemplateReq): TgRespIO[FindTaskTemplateResp]

  def updateTaskTemplate(dto: UpdateTaskTemplateReq): TgRespIO[FindTaskTemplateResp]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskTemplateResp]]

  /** Every template of the project, for the task forms and the label picker; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskTemplateResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findTaskTemplateById(id: UUID): TgRespIO[FindTaskTemplateResp]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskTemplateVersionDto]]

  /** Every version of the template, for the read-only history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[TaskTemplateVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteTaskTemplateById(id: UUID): TgRespIO[TgEmptyBody]
}

class TaskTemplateFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskTemplateFeService {
  val taskTemplateEndpoints = new TaskTemplateEndpoints(securedEndpoint)

  override def createTaskTemplate(dto: CreateTaskTemplateReq): TgRespIO[FindTaskTemplateResp] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.saveEndpoint)(dto)

  override def updateTaskTemplate(dto: UpdateTaskTemplateReq): TgRespIO[FindTaskTemplateResp] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.updateEndpoint)(dto)

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindTaskTemplateResp]] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def findTaskTemplateById(id: UUID): TgRespIO[FindTaskTemplateResp] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.findByIdEndpoint)(id)

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TaskTemplateVersionDto]] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.listVersionsEndpoint)((id, page))

  override def deleteTaskTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskTemplateEndpoints.deleteByIdEndpoint)(id)
}
