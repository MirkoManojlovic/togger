package com.togger.ui.timebox

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.{CreateTimeboxReq, FindTimeboxResp, SaveTimeboxVersionReq, TimeboxVersionDto}
import com.togger.timebox.TimeboxEndpoints

import java.util.UUID

class TimeboxFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  private val timeboxEndpoints = new TimeboxEndpoints(securedEndpoint)

  def createTimebox(dto: CreateTimeboxReq): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.saveEndpoint)(dto)

  def saveNewVersion(dto: SaveTimeboxVersionReq): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.saveNewVersionEndpoint)(dto)

  def findById(id: UUID): TgRespIO[FindTimeboxResp] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.findByIdEndpoint)(id)

  def findByProjectId(projectId: UUID): TgRespIO[Seq[FindTimeboxResp]] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.findByProjectIdEndpoint)(projectId)

  def listVersions(id: UUID): TgRespIO[List[TimeboxVersionDto]] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.listVersionsEndpoint)(id)

  def deleteTimebox(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(timeboxEndpoints.deleteByIdEndpoint)(id)
}
