package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{TaskDumpResp, TaskDumpStatusResp, TaskLoadReq, TaskLoadResp}
import com.togger.dump.TaskDumpEndpoints

import java.util.UUID

class TaskDumpFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskDumpEndpoints = new TaskDumpEndpoints(securedEndpoint)

  def dumpTasks(projectId: UUID): TgRespIO[TaskDumpResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.dumpEndpoint)(projectId)

  def checkStatus(projectId: UUID): TgRespIO[TaskDumpStatusResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.checkStatusEndpoint)(projectId)

  def dumpByConfig(dumpConfigId: UUID): TgRespIO[TaskDumpResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.dumpByConfigEndpoint)(dumpConfigId)

  def checkStatusByConfig(dumpConfigId: UUID): TgRespIO[TaskDumpStatusResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.checkStatusByConfigEndpoint)(dumpConfigId)

  def loadTasks(projectId: UUID, dumpPath: String): TgRespIO[TaskLoadResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.loadEndpoint)((projectId, TaskLoadReq(dumpPath)))
}
