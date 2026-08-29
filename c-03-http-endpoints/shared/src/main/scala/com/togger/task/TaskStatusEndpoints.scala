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

class TaskStatusEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task-status"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskStatusReq, CreateTaskStatusResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskStatusReq])
      .out(jsonBody[TgJsonSuccess[CreateTaskStatusResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTaskStatusResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTaskStatusResp]])
//      .tag(topic)
//      .name(createName(findById))
//      .summary(createSummary(findById))
//      .description(s"Find $topic using id")

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskStatusResp, FindTaskStatusResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskStatusResp])
      .out(jsonBody[TgJsonSuccess[FindTaskStatusResp]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindTaskStatusResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindTaskStatusResp]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

}
