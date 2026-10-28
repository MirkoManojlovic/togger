package com.togger.ui.tgaiaction

import com.togger.ai.TgAiModelEndpoints
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto._
import com.togger.tgaiaction.TgAiActionEndpoints
import com.togger.ui.components.pagination.PaginatedLoader

import java.util.UUID

class TgAiActionFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  val tgAiActionEndpoints = new TgAiActionEndpoints(securedEndpoint)
  private val tgAiModelEndpoints = new TgAiModelEndpoints(securedEndpoint)

  def create(dto: CreateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.saveEndpoint)(dto)

  def update(dto: UpdateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.updateEndpoint)(dto)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgAiActionResp]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findAllEndpoint)(page)

  /** Every action, for the pickers; see [[PaginatedLoader]] for the bound. */
  def findEvery: TgRespIO[Seq[FindTgAiActionResp]] = PaginatedLoader.loadAll(findAll)

  def findById(id: UUID): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findByIdEndpoint)(id)

  def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.deleteByIdEndpoint)(id)

  def listEndpoints: TgRespIO[List[TgAiEndpointDto]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.listEndpointsEndpoint)(())

  def listModels: TgRespIO[List[TgAiModelDto]] =
    protectedBackendClient.protectedHttpCall(tgAiModelEndpoints.listEndpoint)(())

  def findDefaults(projectId: UUID): TgRespIO[List[FindTgAiActionDefaultResp]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findDefaultsEndpoint)(projectId)

  def setDefault(dto: SetTgAiActionDefaultReq): TgRespIO[FindTgAiActionDefaultResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.setDefaultEndpoint)(dto)

  def clearDefault(projectId: UUID, endpointId: Int): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.clearDefaultEndpoint)((projectId, endpointId))
}
