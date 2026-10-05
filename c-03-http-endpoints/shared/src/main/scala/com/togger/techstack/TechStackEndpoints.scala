package com.togger.techstack

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

class TechStackEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "tech-stack"

  val saveEndpoint: TgSecuredEndpoint[CreateTechStackReq, CreateTechStackResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTechStackReq])
      .out(jsonBody[TgJsonSuccess[CreateTechStackResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTechStackResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTechStackResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTechStackResp, FindTechStackResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTechStackResp])
      .out(jsonBody[TgJsonSuccess[FindTechStackResp]])

  val findAllEndpoint: TgSecuredEndpointNoInput[Seq[FindTechStackResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .out(jsonBody[TgJsonSuccess[Seq[FindTechStackResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
