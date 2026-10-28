package com.togger.tgprompttemplate

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

class TgPromptTemplateEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "tg-prompt-template"

  val saveEndpoint: TgSecuredEndpoint[CreateTgPromptTemplateReq, FindTgPromptTemplateResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTgPromptTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindTgPromptTemplateResp]])

  val newVersionEndpoint: TgSecuredEndpoint[NewTgPromptTemplateVersionReq, FindTgPromptTemplateResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[NewTgPromptTemplateVersionReq])
      .out(jsonBody[TgJsonSuccess[FindTgPromptTemplateResp]])

  val findAllEndpoint: TgSecuredEndpoint[PageRequest, PaginatedResponse[FindTgPromptTemplateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath)
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindTgPromptTemplateResp]]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTgPromptTemplateResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTgPromptTemplateResp]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[TgPromptTemplateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[TgPromptTemplateVersionDto]]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
