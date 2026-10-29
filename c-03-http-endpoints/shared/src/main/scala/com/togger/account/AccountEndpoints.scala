package com.togger.account
import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgEndpoints.TgSecuredEndpointEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto.{AccountManagementDto, AccountTypeDto, SearchAccountDto, UpdateAccountReq}
import io.circe.generic.auto._
import sttp.tapir.generic.auto.schemaForCaseClass
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, path}

import java.util.UUID

class AccountEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "account"

  def searchByFirstNameLastNameEmail: TgSecuredEndpoint[(String, PageRequest), PaginatedResponse[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "term" / path[String]("term"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[SearchAccountDto]]])


  // The account management endpoints below are admin only.
  val findAllEndpoint: TgSecuredEndpoint[PageRequest, PaginatedResponse[AccountManagementDto]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[AccountManagementDto]]])

  val findAccountTypesEndpoint: TgSecuredEndpoint[Unit, List[AccountTypeDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "types")
      .out(jsonBody[TgJsonSuccess[List[AccountTypeDto]]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, AccountManagementDto] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[AccountManagementDto]])

  val updateEndpoint: TgSecuredEndpoint[UpdateAccountReq, AccountManagementDto] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateAccountReq])
      .out(jsonBody[TgJsonSuccess[AccountManagementDto]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
