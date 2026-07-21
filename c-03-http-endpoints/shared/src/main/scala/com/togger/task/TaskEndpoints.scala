package com.togger.task

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{CreateTaskAndLinksReq, FindTaskResp, FindTaskRespFull, UpdateTaskReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TaskEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskAndLinksReq, FindTaskResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskAndLinksReq])
      .out(jsonBody[TgJsonSuccess[FindTaskResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, FindTaskResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[FindTaskResp]])
//      .tag(topic)
//      .name(createName(findById))
//      .summary(createSummary(findById))
//      .description(s"Find $topic using id")

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[FindTaskRespFull]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project-id" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[FindTaskRespFull]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskReq, FindTaskResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskReq])
      .out(jsonBody[TgJsonSuccess[FindTaskResp]])

}
