package com.togger.taskloadconfig

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.taskloadconfig.{CreateTaskLoadConfigReq, TaskLoadConfigResp, UpdateTaskLoadConfigReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TaskLoadConfigEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task-load-config"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskLoadConfigReq, TaskLoadConfigResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskLoadConfigReq])
      .out(jsonBody[TgJsonSuccess[TaskLoadConfigResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, TaskLoadConfigResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TaskLoadConfigResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskLoadConfigReq, TaskLoadConfigResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskLoadConfigReq])
      .out(jsonBody[TgJsonSuccess[TaskLoadConfigResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[TaskLoadConfigResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[TaskLoadConfigResp]]])

  val findByAccountIdEndpoint: TgSecuredEndpoint[UUID, Seq[TaskLoadConfigResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "account" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[TaskLoadConfigResp]]])

}
