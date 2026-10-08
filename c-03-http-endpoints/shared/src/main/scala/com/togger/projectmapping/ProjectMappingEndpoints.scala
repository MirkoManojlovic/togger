package com.togger.projectmapping

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class ProjectMappingEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project-mapping"

  val saveEndpoint: TgSecuredEndpoint[CreateProjectMappingReq, ProjectMappingResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateProjectMappingReq])
      .out(jsonBody[TgJsonSuccess[ProjectMappingResp]])

  val findByProjectIdDbEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[ProjectMappingResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project-db" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[ProjectMappingResp]]])

  val findByProjectIdDumpEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[ProjectMappingResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project-dump" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[ProjectMappingResp]]])

  val deleteEndpoint: TgSecuredEndpointEmptyBody[(UUID, UUID)] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("projectIdDb") / path[UUID]("projectIdDump"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
