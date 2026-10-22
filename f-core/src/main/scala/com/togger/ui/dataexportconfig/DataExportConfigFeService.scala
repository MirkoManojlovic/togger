package com.togger.ui.dataexportconfig

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.dataexportconfig.{CreateDataExportConfigReq, DataExportConfigResp, UpdateDataExportConfigReq}
import com.togger.dataexportconfig.DataExportConfigEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait DataExportConfigFeService {
  def create(dto: CreateDataExportConfigReq): TgRespIO[DataExportConfigResp]

  def findById(id: UUID): TgRespIO[DataExportConfigResp]

  def update(dto: UpdateDataExportConfigReq): TgRespIO[DataExportConfigResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[DataExportConfigResp]]

  /** Every config of the project, for the runs over all of them; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataExportConfigResp]]
}

class DataExportConfigFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends DataExportConfigFeService {

  private val dataExportConfigEndpoints = new DataExportConfigEndpoints(securedEndpoint)

  override def create(dto: CreateDataExportConfigReq): TgRespIO[DataExportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataExportConfigEndpoints.saveEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[DataExportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataExportConfigEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateDataExportConfigReq): TgRespIO[DataExportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataExportConfigEndpoints.updateEndpoint)(dto)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(dataExportConfigEndpoints.deleteByIdEndpoint)(id)

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[DataExportConfigResp]] =
    protectedBackendClient.protectedHttpCall(dataExportConfigEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataExportConfigResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))

}
