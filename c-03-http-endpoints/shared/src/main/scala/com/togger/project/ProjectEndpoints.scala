package com.togger.project

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{CreateProjectReq, FindProjectResp, SearchAccountDto, SearchProjectsByLabelsReq, UpdateProjectReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class ProjectEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project"

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindProjectResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindProjectResp]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val saveEndpoint: TgSecuredEndpoint[CreateProjectReq, FindProjectResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateProjectReq])
      .out(jsonBody[TgJsonSuccess[FindProjectResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateProjectReq, FindProjectResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateProjectReq])
      .out(jsonBody[TgJsonSuccess[FindProjectResp]])

  val findByOrganizationIdAndAccountIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindProjectResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "organization" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindProjectResp]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllMembersEndpoint: TgSecuredEndpoint[UUID, Seq[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "members" / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[Seq[SearchAccountDto]]])

  val searchByLabelsEndpoint: TgSecuredEndpoint[SearchProjectsByLabelsReq, Seq[FindProjectResp]] =
    securedEndpoint.endpoint.post
      .in(basePath / "search")
      .in(jsonBody[SearchProjectsByLabelsReq])
      .out(jsonBody[TgJsonSuccess[Seq[FindProjectResp]]])

}
