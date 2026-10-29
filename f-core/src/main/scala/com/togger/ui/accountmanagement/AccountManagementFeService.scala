package com.togger.ui.accountmanagement

import com.togger.account.AccountEndpoints
import com.togger.auth.SecuredEndpoint
import com.togger.backendcalls.ProtectedBackendClient
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{PageRequest, PaginatedResponse}
import com.togger.dto.{AccountManagementDto, AccountTypeDto, UpdateAccountReq}

import java.util.UUID

class AccountManagementFeService(securedEndpoint: SecuredEndpoint, protectedBackendClient: ProtectedBackendClient) {
  private val accountEndpoints = new AccountEndpoints(securedEndpoint)

  def findAll(page: PageRequest): TgRespIO[PaginatedResponse[AccountManagementDto]] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findAllEndpoint)(page)

  def findById(id: UUID): TgRespIO[AccountManagementDto] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findByIdEndpoint)(id)

  def findAccountTypes: TgRespIO[List[AccountTypeDto]] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.findAccountTypesEndpoint)(())

  def update(req: UpdateAccountReq): TgRespIO[AccountManagementDto] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.updateEndpoint)(req)

  def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    protectedBackendClient.protectedHttpCall(accountEndpoints.deleteByIdEndpoint)(id)
}
