package com.togger.task

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.PaginationInputs.pageQuery
import com.togger.common.{PageRequest, PaginatedResponse, TgJsonSuccess}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto._
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TaskTemplateEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task-template"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskTemplateReq, FindTaskTemplateResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindTaskTemplateResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTaskTemplateResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTaskTemplateResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskTemplateReq, FindTaskTemplateResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskTemplateReq])
      .out(jsonBody[TgJsonSuccess[FindTaskTemplateResp]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[FindTaskTemplateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[FindTaskTemplateResp]]])

  val findByLabelEndpoint: TgSecuredEndpoint[UUID, Option[FindTaskTemplateResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "label" / path[UUID]("labelId"))
      .out(jsonBody[TgJsonSuccess[Option[FindTaskTemplateResp]]])

  val listVersionsEndpoint: TgSecuredEndpoint[(UUID, PageRequest), PaginatedResponse[TaskTemplateVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .in(pageQuery)
      .out(jsonBody[TgJsonSuccess[PaginatedResponse[TaskTemplateVersionDto]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
