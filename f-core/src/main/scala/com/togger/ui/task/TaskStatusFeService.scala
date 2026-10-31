package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.task.TaskStatusEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait TaskStatusFeService {
  def createTaskStatus(dto: CreateTaskStatusReq): TgRespIO[CreateTaskStatusResp]

  def updateTaskStatus(dto: UpdateTaskStatusResp): TgRespIO[FindTaskStatusResp]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskStatusResp]]

  /** Every status of the project, for the pickers and filters offering all of them; see [[PaginatedLoader]] for the
    * bound.
    */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskStatusResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findTaskStatusById(id: UUID): TgRespIO[FindTaskStatusResp]

  def deleteTaskStatusById(id: UUID): TgRespIO[TgEmptyBody]
}

class TaskStatusFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskStatusFeService {
  val taskStatusEndpoints = new TaskStatusEndpoints(securedEndpoint)

  override def createTaskStatus(dto: CreateTaskStatusReq): TgRespIO[CreateTaskStatusResp] =
    protectedBackendClient.protectedHttpCall(taskStatusEndpoints.saveEndpoint)(dto)

  override def updateTaskStatus(dto: UpdateTaskStatusResp): TgRespIO[FindTaskStatusResp] =
    protectedBackendClient.protectedHttpCall(taskStatusEndpoints.updateEndpoint)(dto)

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskStatusResp]] =
    protectedBackendClient.protectedHttpCall(taskStatusEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def findTaskStatusById(id: UUID): TgRespIO[FindTaskStatusResp] =
    protectedBackendClient.protectedHttpCall(taskStatusEndpoints.findByIdEndpoint)(id)

  override def deleteTaskStatusById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskStatusEndpoints.deleteByIdEndpoint)(id)
}
