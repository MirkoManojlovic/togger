package com.togger.task

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

class TaskLabelEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task-label"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskLabelReq, CreateTaskLabelResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskLabelReq])
      .out(jsonBody[TgJsonSuccess[CreateTaskLabelResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTaskLabelResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTaskLabelResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskLabelResp, FindTaskLabelResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskLabelResp])
      .out(jsonBody[TgJsonSuccess[FindTaskLabelResp]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindTaskLabelResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindTaskLabelResp]]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
