package com.togger.project

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
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

  /** Deletes the project with all of its data (tasks, timeboxes, milestones, ...). */
  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByOrganizationIdAndAccountIdEndpoint
    : TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindProjectResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "organization" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindProjectResp]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val findAllMembersEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[SearchAccountDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "members" / path[UUID]("project-id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[SearchAccountDto]]])

  // The labels stay in the body; only the page travels in the query string.
  val searchByLabelsEndpoint
    : TgSecuredEndpoint[(SearchProjectsByLabelsReq, PageRequest), PaginatedResponse[FindProjectResp]] =
    securedEndpoint.endpoint.post
      .in(basePath / "search")
      .in(jsonBody[SearchProjectsByLabelsReq])
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindProjectResp]]])

}
