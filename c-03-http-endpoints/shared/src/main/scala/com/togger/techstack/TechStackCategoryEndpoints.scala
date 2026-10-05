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

class TechStackCategoryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "tech-stack-category"

  val saveEndpoint: TgSecuredEndpoint[CreateTechStackCategoryReq, CreateTechStackCategoryResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTechStackCategoryReq])
      .out(jsonBody[TgJsonSuccess[CreateTechStackCategoryResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTechStackCategoryResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTechStackCategoryResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTechStackCategoryResp, FindTechStackCategoryResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTechStackCategoryResp])
      .out(jsonBody[TgJsonSuccess[FindTechStackCategoryResp]])

  val findAllEndpoint: TgSecuredEndpointNoInput[Seq[FindTechStackCategoryResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .out(jsonBody[TgJsonSuccess[Seq[FindTechStackCategoryResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
