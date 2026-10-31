package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.task.TaskLabelEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait TaskLabelFeService {
  def createTaskLabel(dto: CreateTaskLabelReq): TgRespIO[CreateTaskLabelResp]

  def updateTaskLabel(dto: UpdateTaskLabelResp): TgRespIO[FindTaskLabelResp]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskLabelResp]]

  /** Every label of the project, for the pickers and filters offering all of them; see [[PaginatedLoader]] for the
    * bound.
    */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[FindTaskLabelResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

  def findTaskLabelById(id: UUID): TgRespIO[FindTaskLabelResp]

  def deleteTaskLabelById(id: UUID): TgRespIO[TgEmptyBody]
}

class TaskLabelFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskLabelFeService {
  val taskLabelEndpoints = new TaskLabelEndpoints(securedEndpoint)

  override def createTaskLabel(dto: CreateTaskLabelReq): TgRespIO[CreateTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.saveEndpoint)(dto)

  override def updateTaskLabel(dto: UpdateTaskLabelResp): TgRespIO[FindTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.updateEndpoint)(dto)

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTaskLabelResp]] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def findTaskLabelById(id: UUID): TgRespIO[FindTaskLabelResp] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.findByIdEndpoint)(id)

  override def deleteTaskLabelById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskLabelEndpoints.deleteByIdEndpoint)(id)
}
