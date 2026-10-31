package com.togger.ui.accountmanagement

import com.togger.account.AccountEndpoints
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{AccountManagementDto, AccountTypeDto, UpdateAccountReq}

import java.util.UUID

trait AccountManagementFeService {
  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[AccountManagementDto]]

  def findById(id: UUID): TgRespIO[AccountManagementDto]

  def findAccountTypes: TgRespIO[List[AccountTypeDto]]

  def update(req: UpdateAccountReq): TgRespIO[AccountManagementDto]

  def deleteById(id: UUID): TgRespIO[TgEmptyBody]
}

class AccountManagementFeServiceImpl(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient)
    extends AccountManagementFeService {
  private val accountEndpoints = new AccountEndpoints(securedEndpoint)

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[AccountManagementDto]] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findAllEndpoint)(page)

  override def findById(id: UUID): TgRespIO[AccountManagementDto] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findByIdEndpoint)(id)

  override def findAccountTypes: TgRespIO[List[AccountTypeDto]] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findAccountTypesEndpoint)(())

  override def update(req: UpdateAccountReq): TgRespIO[AccountManagementDto] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.updateEndpoint)(req)

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.deleteByIdEndpoint)(id)
}
