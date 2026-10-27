package com.togger.tgprompt

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

class TgPromptEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "tg-prompt"

  val saveEndpoint: TgSecuredEndpoint[CreateTgPromptReq, FindTgPromptResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTgPromptReq])
      .out(jsonBody[TgJsonSuccess[FindTgPromptResp]])

  val newVersionEndpoint: TgSecuredEndpoint[NewTgPromptVersionReq, FindTgPromptResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[NewTgPromptVersionReq])
      .out(jsonBody[TgJsonSuccess[FindTgPromptResp]])

  val findAllEndpoint: TgSecuredEndpoint[PageRequest, PaginatedResponse[FindTgPromptResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindTgPromptResp]]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTgPromptResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTgPromptResp]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[TgPromptVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[TgPromptVersionDto]]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
