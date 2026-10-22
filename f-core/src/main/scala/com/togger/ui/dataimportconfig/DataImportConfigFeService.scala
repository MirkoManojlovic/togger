package com.togger.ui.dataimportconfig

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.dataimportconfig.{CreateDataImportConfigReq, DataImportConfigResp, UpdateDataImportConfigReq}
import com.togger.dataimportconfig.DataImportConfigEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait DataImportConfigFeService {
  def create(dto: CreateDataImportConfigReq): TgRespIO[DataImportConfigResp]

  def findById(id: UUID): TgRespIO[DataImportConfigResp]

  def update(dto: UpdateDataImportConfigReq): TgRespIO[DataImportConfigResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[DataImportConfigResp]]

  /** Every config of the project, for the runs over all of them; see [[PaginatedLoader]] for the bound. */
  def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataImportConfigResp]]
}

class DataImportConfigFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends DataImportConfigFeService {

  private val dataImportConfigEndpoints = new DataImportConfigEndpoints(securedEndpoint)

  override def create(dto: CreateDataImportConfigReq): TgRespIO[DataImportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataImportConfigEndpoints.saveEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[DataImportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataImportConfigEndpoints.findByIdEndpoint)(id)

  override def update(dto: UpdateDataImportConfigReq): TgRespIO[DataImportConfigResp] =
    protectedBackendClient.protectedHttpCall(dataImportConfigEndpoints.updateEndpoint)(dto)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(dataImportConfigEndpoints.deleteByIdEndpoint)(id)

  override def findByProjectId(
    projectId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[DataImportConfigResp]] =
    protectedBackendClient.protectedHttpCall(dataImportConfigEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def findAllByProjectId(projectId: UUID): TgRespIO[Seq[DataImportConfigResp]] =
    PaginatedLoader.loadAll(findByProjectId(projectId, _))
}
