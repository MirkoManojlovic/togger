package com.togger.organization

import com.togger.auth.SecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgEndpoints._
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{CreateOrganizationReq, FindOrganizationResp, UpdateOrganizationReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class OrganizationEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "organization"

  val saveEndpoint: TgSecuredEndpoint[CreateOrganizationReq, FindOrganizationResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateOrganizationReq])
      .out(jsonBody[TgJsonSuccess[FindOrganizationResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindOrganizationResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindOrganizationResp]])
//      .tag(topic)
//      .name(createName(findById))
//      .summary(createSummary(findById))
//      .description(s"Find $topic using id")

  val updateEndpoint: TgSecuredEndpoint[UpdateOrganizationReq, FindOrganizationResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateOrganizationReq])
      .out(jsonBody[TgJsonSuccess[FindOrganizationResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllEndpoint: TgSecuredEndpoint[PageRequest, PaginatedResponse[FindOrganizationResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindOrganizationResp]]])

  val findByAccountIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindOrganizationResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "account" / path[UUID]("account-id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindOrganizationResp]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllMembersEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "members" / path[UUID]("organization-id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[SearchAccountDto]]])
}
