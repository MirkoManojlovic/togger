package com.togger.organization

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{
  CreateOrganizationReq,
  FindOrganizationResp,
  FindOrganizationsAndProjects,
  UpdateOrganizationReq
}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class OrganizationEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "organization"

  val saveEndpoint: TgSecuredEndpoint[CreateOrganizationReq, FindOrganizationResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateOrganizationReq])
      .out(jsonBody[TgJsonSuccess[FindOrganizationResp]])

  val findByIdEndpoint: TgSecuredEndpoint[Long, FindOrganizationResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[Long]("id"))
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

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[Long] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[Long]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllEndpoint: TgSecuredEndpointNoInput[Seq[FindOrganizationResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .out(jsonBody[TgJsonSuccess[Seq[FindOrganizationResp]]])

  val findByAccountIdEndpoint: TgSecuredEndpoint[Long, Seq[FindOrganizationResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "account" / path[Long]("account-id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindOrganizationResp]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllMembersEndpoint: TgSecuredEndpoint[Long, Seq[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "members" / path[Long]("organization-id"))
      .out(jsonBody[TgJsonSuccess[Seq[SearchAccountDto]]])

  val findOrgAndProjectsByAccountIdEndpoint: TgSecuredEndpoint[Long, Seq[FindOrganizationsAndProjects]] =
    securedEndpoint.endpoint.get
      .in(basePath / "org-projects" / "account" / path[Long]("account-id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindOrganizationsAndProjects]]])
}
