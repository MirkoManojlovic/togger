package com.togger.ui.task

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{DataExportResp, DataExportStatusResp, DataImportReq, DataImportResp, DataSyncPendingResp}
import com.togger.dump.DataExportEndpoints

import java.util.UUID

trait DataExportFeService {
  def exportData(projectId: UUID): TgRespIO[DataExportResp]

  def checkStatus(projectId: UUID): TgRespIO[DataExportStatusResp]

  def exportByConfig(exportConfigId: UUID): TgRespIO[DataExportResp]

  def checkStatusByConfig(exportConfigId: UUID): TgRespIO[DataExportStatusResp]

  def importData(projectId: UUID, importPath: String): TgRespIO[DataImportResp]

  def importNewProject(organizationId: UUID, importPath: String): TgRespIO[DataImportResp]

  def checkImportStatus(projectId: UUID, importPath: String): TgRespIO[DataExportStatusResp]

  def exportPending(projectId: UUID): TgRespIO[DataSyncPendingResp]

  def importPending(projectId: UUID): TgRespIO[DataSyncPendingResp]
}

class DataExportFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends DataExportFeService {
  val dataExportEndpoints = new DataExportEndpoints(securedEndpoint)

  override def exportData(projectId: UUID): TgRespIO[DataExportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.exportEndpoint)(projectId)

  override def checkStatus(projectId: UUID): TgRespIO[DataExportStatusResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.checkStatusEndpoint)(projectId)

  override def exportByConfig(exportConfigId: UUID): TgRespIO[DataExportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.exportByConfigEndpoint)(exportConfigId)

  override def checkStatusByConfig(exportConfigId: UUID): TgRespIO[DataExportStatusResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.checkStatusByConfigEndpoint)(exportConfigId)

  override def importData(projectId: UUID, importPath: String): TgRespIO[DataImportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.importEndpoint)((projectId, DataImportReq(importPath)))

  override def importNewProject(organizationId: UUID, importPath: String): TgRespIO[DataImportResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.importNewProjectEndpoint)(
      (organizationId, DataImportReq(importPath))
    )

  override def checkImportStatus(projectId: UUID, importPath: String): TgRespIO[DataExportStatusResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.checkImportStatusEndpoint)(
      (projectId, DataImportReq(importPath))
    )

  override def exportPending(projectId: UUID): TgRespIO[DataSyncPendingResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.exportPendingEndpoint)(projectId)

  override def importPending(projectId: UUID): TgRespIO[DataSyncPendingResp] =
    protectedBackendClient.protectedHttpCall(dataExportEndpoints.importPendingEndpoint)(projectId)
}
