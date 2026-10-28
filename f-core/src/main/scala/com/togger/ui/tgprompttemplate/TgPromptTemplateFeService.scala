package com.togger.ui.tgprompttemplate

import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.tgprompttemplate.TgPromptTemplateEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class TgPromptTemplateFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val tgPromptTemplateEndpoints = new TgPromptTemplateEndpoints(securedEndpoint)

  def createTgPromptTemplate(dto: CreateTgPromptTemplateReq): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.saveEndpoint)(dto)

  def saveNewVersion(dto: NewTgPromptTemplateVersionReq): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.newVersionEndpoint)(dto)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgPromptTemplateResp]] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.findAllEndpoint)(page)

  def findTgPromptTemplateById(id: UUID): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.findByIdEndpoint)(id)

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TgPromptTemplateVersionDto]] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.listVersionsEndpoint)((id, page))

  /** Every version of the prompt, for the history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[TgPromptTemplateVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.deleteVersionEndpoint)(version)

  def deleteTgPromptTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.deleteByIdEndpoint)(id)
}
