package com.togger.tgaiaction

import com.togger.auth.SecuredEndpoint
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.dto._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TgAiActionEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "tg-ai-action"

  val saveEndpoint: TgSecuredEndpoint[CreateTgAiActionReq, FindTgAiActionResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTgAiActionReq])
      .out(jsonBody[TgJsonSuccess[FindTgAiActionResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTgAiActionReq, FindTgAiActionResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTgAiActionReq])
      .out(jsonBody[TgJsonSuccess[FindTgAiActionResp]])

  val findAllEndpoint: TgSecuredEndpoint[PageRequest, PaginatedResponse[FindTgAiActionResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindTgAiActionResp]]])

  val listEndpointsEndpoint: TgSecuredEndpoint[Unit, List[TgAiEndpointDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / "endpoints")
      .out(jsonBody[TgJsonSuccess[List[TgAiEndpointDto]]])

  val findDefaultsEndpoint: TgSecuredEndpoint[UUID, List[FindTgAiActionDefaultResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "default")
      .in(query[UUID]("projectId"))
      .out(jsonBody[TgJsonSuccess[List[FindTgAiActionDefaultResp]]])

  val setDefaultEndpoint: TgSecuredEndpoint[SetTgAiActionDefaultReq, FindTgAiActionDefaultResp] =
    securedEndpoint.endpoint.put
      .in(basePath / "default")
      .in(jsonBody[SetTgAiActionDefaultReq])
      .out(jsonBody[TgJsonSuccess[FindTgAiActionDefaultResp]])

  val clearDefaultEndpoint: TgSecuredEndpointEmptyBody[(UUID, Int)] =
    securedEndpoint.endpoint.delete
      .in(basePath / "default" / path[UUID]("projectId") / path[Int]("endpointId"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTgAiActionResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTgAiActionResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
