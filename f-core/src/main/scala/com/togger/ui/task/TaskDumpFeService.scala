package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.TaskDumpResp
import com.togger.dump.TaskDumpEndpoints

import java.util.UUID

class TaskDumpFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val taskDumpEndpoints = new TaskDumpEndpoints(securedEndpoint)

  def dumpTasks(projectId: UUID): TgRespIO[TaskDumpResp] =
    protectedBackendClient.protectedHttpCall(taskDumpEndpoints.dumpEndpoint)(projectId)
}
