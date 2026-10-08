package com.togger.accountmapping

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.accountmapping.{AccountMappingResp, CreateAccountMappingReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class AccountMappingEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "account-mapping"

  val saveEndpoint: TgSecuredEndpoint[CreateAccountMappingReq, AccountMappingResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateAccountMappingReq])
      .out(jsonBody[TgJsonSuccess[AccountMappingResp]])

  val findByAccountIdDbEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[AccountMappingResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "account-db" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[AccountMappingResp]]])

  val findByAccountIdDumpEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[AccountMappingResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "account-dump" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[AccountMappingResp]]])

  val deleteEndpoint: TgSecuredEndpointEmptyBody[(UUID, UUID)] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("accountIdDb") / path[UUID]("accountIdDump"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
