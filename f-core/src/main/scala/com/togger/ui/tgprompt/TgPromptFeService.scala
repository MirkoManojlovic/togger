package com.togger.ui.tgprompt

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.tgprompt.TgPromptEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class TgPromptFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val tgPromptEndpoints = new TgPromptEndpoints(securedEndpoint)

  def createTgPrompt(dto: CreateTgPromptReq): TgRespIO[FindTgPromptResp] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.saveEndpoint)(dto)

  def saveNewVersion(dto: NewTgPromptVersionReq): TgRespIO[FindTgPromptResp] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.newVersionEndpoint)(dto)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgPromptResp]] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.findAllEndpoint)(page)

  def findTgPromptById(id: UUID): TgRespIO[FindTgPromptResp] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.findByIdEndpoint)(id)

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TgPromptVersionDto]] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.listVersionsEndpoint)((id, page))

  /** Every version of the prompt, for the history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[TgPromptVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.deleteVersionEndpoint)(version)

  def deleteTgPromptById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptEndpoints.deleteByIdEndpoint)(id)
}
