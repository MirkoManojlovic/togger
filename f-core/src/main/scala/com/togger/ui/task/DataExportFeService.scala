package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{DataExportResp, DataExportStatusResp, TaskLoadReq, TaskLoadResp}
import com.togger.dump.DataExportEndpoints

import java.util.UUID

class DataExportFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val dataExportEndpoints = new DataExportEndpoints(securedEndpoint)

  def exportData(projectId: UUID): TgRespIO[DataExportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.exportEndpoint)(projectId)

  def checkStatus(projectId: UUID): TgRespIO[DataExportStatusResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.checkStatusEndpoint)(projectId)

  def exportByConfig(exportConfigId: UUID): TgRespIO[DataExportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.exportByConfigEndpoint)(exportConfigId)

  def checkStatusByConfig(exportConfigId: UUID): TgRespIO[DataExportStatusResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.checkStatusByConfigEndpoint)(exportConfigId)

  def loadTasks(projectId: UUID, dumpPath: String): TgRespIO[TaskLoadResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.loadEndpoint)((projectId, TaskLoadReq(dumpPath)))
}
