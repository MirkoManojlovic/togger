package com.togger.ui.taskdumpconfig

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.taskdumpconfig.{CreateTaskDumpConfigReq, TaskDumpConfigResp, UpdateTaskDumpConfigReq}
import com.togger.taskdumpconfig.TaskDumpConfigEndpoints

import java.util.UUID

trait TaskDumpConfigFeService {
  def create(dto: CreateTaskDumpConfigReq): TgRespIO[TaskDumpConfigResp]

  def findById(id: UUID): TgRespIO[TaskDumpConfigResp]

  def update(dto: UpdateTaskDumpConfigReq): TgRespIO[TaskDumpConfigResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectId(projectId: UUID): TgRespIO[Seq[TaskDumpConfigResp]]
}

class TaskDumpConfigFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TaskDumpConfigFeService {

  private val taskDumpConfigEndpoints = new TaskDumpConfigEndpoints(securedEndpoint)

  override def create(dto: CreateTaskDumpConfigReq): TgRespIO[TaskDumpConfigResp] =
    protectedBackendClient.protectedHttpCall(taskDumpConfigEndpoints.saveEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[TaskDumpConfigResp] =
    protectedBackendClient.protectedHttpCall(taskDumpConfigEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateTaskDumpConfigReq): TgRespIO[TaskDumpConfigResp] =
    protectedBackendClient.protectedHttpCall(taskDumpConfigEndpoints.updateEndpoint)(dto)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(taskDumpConfigEndpoints.deleteByIdEndpoint)(id)

  override def findByProjectId(projectId: UUID): TgRespIO[Seq[TaskDumpConfigResp]] =
    protectedBackendClient.protectedHttpCall(taskDumpConfigEndpoints.findByProjectIdEndpoint)(projectId)

}
