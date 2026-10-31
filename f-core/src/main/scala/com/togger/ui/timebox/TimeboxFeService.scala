package com.togger.ui.timebox

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{CreateTimeboxReq, FindTimeboxResp, SaveTimeboxVersionReq, TimeboxVersionDto}
import com.togger.timebox.TimeboxEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

trait TimeboxFeService {
  def createTimebox(dto: CreateTimeboxReq): TgRespIO[FindTimeboxResp]

  def saveNewVersion(dto: SaveTimeboxVersionReq): TgRespIO[FindTimeboxResp]

  def findById(id: UUID): TgRespIO[FindTimeboxResp]

  def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTimeboxResp]]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TimeboxVersionDto]]

  /** The whole history of the timebox, newest version first, for the history screen; see [[PaginatedLoader]] for the
    * bound.
    */
  def listAllVersions(id: UUID): TgRespIO[Seq[TimeboxVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteTimebox(id: UUID): TgRespIO[TgEmptyBody]
}

class TimeboxFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TimeboxFeService {
  private val timeboxEndpoints = new TimeboxEndpoints(securedEndpoint)

  override def createTimebox(dto: CreateTimeboxReq): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.saveEndpoint)(dto)

  override def saveNewVersion(dto: SaveTimeboxVersionReq): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.saveNewVersionEndpoint)(dto)

  override def findById(id: UUID): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.findByIdEndpoint)(id)

  override def findByProjectId(projectId: UUID, page: PageRequest): TgRespIO[PaginatedResponse[FindTimeboxResp]] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.findByProjectIdEndpoint)((projectId, page))

  override def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TimeboxVersionDto]] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.listVersionsEndpoint)((id, page))

  override def deleteTimebox(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.deleteByIdEndpoint)(id)
}
