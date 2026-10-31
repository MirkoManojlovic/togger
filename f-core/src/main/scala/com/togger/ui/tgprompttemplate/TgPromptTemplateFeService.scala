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

trait TgPromptTemplateFeService {
  def createTgPromptTemplate(dto: CreateTgPromptTemplateReq): TgRespIO[FindTgPromptTemplateResp]

  def saveNewVersion(dto: NewTgPromptTemplateVersionReq): TgRespIO[FindTgPromptTemplateResp]

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgPromptTemplateResp]]

  /** Every prompt template, for the pickers; see [[PaginatedLoader]] for the bound. */
  def findAllTemplates: TgRespIO[Seq[FindTgPromptTemplateResp]] = PaginatedLoader.loadAll(findAll)

  def findTgPromptTemplateById(id: UUID): TgRespIO[FindTgPromptTemplateResp]

  def listVersions(id: UUID, page: PageRequest): TgRespIO[PaginatedResponse[TgPromptTemplateVersionDto]]

  /** Every version of the prompt, for the history; see [[PaginatedLoader]] for the bound. */
  def listAllVersions(id: UUID): TgRespIO[Seq[TgPromptTemplateVersionDto]] =
    PaginatedLoader.loadAll(listVersions(id, _))

  def deleteVersion(version: UUID): TgRespIO[TgEmptyBody]

  def deleteTgPromptTemplateById(id: UUID): TgRespIO[TgEmptyBody]
}

class TgPromptTemplateFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TgPromptTemplateFeService {
  val tgPromptTemplateEndpoints = new TgPromptTemplateEndpoints(securedEndpoint)

  override def createTgPromptTemplate(dto: CreateTgPromptTemplateReq): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.saveEndpoint)(dto)

  override def saveNewVersion(dto: NewTgPromptTemplateVersionReq): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.newVersionEndpoint)(dto)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgPromptTemplateResp]] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.findAllEndpoint)(page)

  override def findTgPromptTemplateById(id: UUID): TgRespIO[FindTgPromptTemplateResp] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.findByIdEndpoint)(id)

  override def listVersions(
    id: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[TgPromptTemplateVersionDto]] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.listVersionsEndpoint)((id, page))

  override def deleteVersion(version: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.deleteVersionEndpoint)(version)

  override def deleteTgPromptTemplateById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgPromptTemplateEndpoints.deleteByIdEndpoint)(id)
}
