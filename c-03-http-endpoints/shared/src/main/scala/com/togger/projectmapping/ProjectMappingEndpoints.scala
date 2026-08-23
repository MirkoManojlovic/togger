package com.togger.projectmapping

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.projectmapping.{CreateProjectMappingReq, ProjectMappingResp, UpdateProjectMappingReq}
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

  val findByIdEndpoint: TgSecuredEndpoint[UUID, ProjectMappingResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[ProjectMappingResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateProjectMappingReq, ProjectMappingResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateProjectMappingReq])
      .out(jsonBody[TgJsonSuccess[ProjectMappingResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByProjectIdLoadedEndpoint: TgSecuredEndpoint[UUID, Seq[ProjectMappingResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project-loaded" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[ProjectMappingResp]]])

}
