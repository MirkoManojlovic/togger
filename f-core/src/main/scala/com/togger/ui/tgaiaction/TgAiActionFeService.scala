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

trait TgAiActionFeService {
  def create(dto: CreateTgAiActionReq): TgRespIO[FindTgAiActionResp]

  def update(dto: UpdateTgAiActionReq): TgRespIO[FindTgAiActionResp]

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgAiActionResp]]

  /** Every action, for the pickers; see [[PaginatedLoader]] for the bound. */
  def findEvery: TgRespIO[Seq[FindTgAiActionResp]] = PaginatedLoader.loadAll(findAll)

  def findById(id: UUID): TgRespIO[FindTgAiActionResp]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]

  def listEndpoints: TgRespIO[List[TgAiEndpointDto]]

  def listModels: TgRespIO[List[TgAiModelDto]]

  def findDefaults(projectId: UUID): TgRespIO[List[FindTgAiActionDefaultResp]]

  def setDefault(dto: SetTgAiActionDefaultReq): TgRespIO[FindTgAiActionDefaultResp]

  def clearDefault(projectId: UUID, endpointId: Int): TgRespIO[TgEmptyBody]
}

class TgAiActionFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends TgAiActionFeService {
  val tgAiActionEndpoints = new TgAiActionEndpoints(securedEndpoint)
  private val tgAiModelEndpoints = new TgAiModelEndpoints(securedEndpoint)

  override def create(dto: CreateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.saveEndpoint)(dto)

  override def update(dto: UpdateTgAiActionReq): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.updateEndpoint)(dto)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindTgAiActionResp]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findAllEndpoint)(page)

  override def findById(id: UUID): TgRespIO[FindTgAiActionResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findByIdEndpoint)(id)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.deleteByIdEndpoint)(id)

  override def listEndpoints: TgRespIO[List[TgAiEndpointDto]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.listEndpointsEndpoint)(())

  override def listModels: TgRespIO[List[TgAiModelDto]] =
    protectedBackendClient.protectedHttpCall(tgAiModelEndpoints.listEndpoint)(())

  override def findDefaults(projectId: UUID): TgRespIO[List[FindTgAiActionDefaultResp]] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.findDefaultsEndpoint)(projectId)

  override def setDefault(dto: SetTgAiActionDefaultReq): TgRespIO[FindTgAiActionDefaultResp] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.setDefaultEndpoint)(dto)

  override def clearDefault(projectId: UUID, endpointId: Int): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(tgAiActionEndpoints.clearDefaultEndpoint)((projectId, endpointId))
}
