package com.togger.projectlabel

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class ProjectLabelEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project-label"

  val saveEndpoint: TgSecuredEndpoint[CreateProjectLabelReq, CreateProjectLabelResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateProjectLabelReq])
      .out(jsonBody[TgJsonSuccess[CreateProjectLabelResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindProjectLabelResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindProjectLabelResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateProjectLabelResp, FindProjectLabelResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateProjectLabelResp])
      .out(jsonBody[TgJsonSuccess[FindProjectLabelResp]])

  val findAllEndpoint: TgSecuredEndpointNoInput[Seq[FindProjectLabelResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .out(jsonBody[TgJsonSuccess[Seq[FindProjectLabelResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
