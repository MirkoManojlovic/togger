package com.togger.taskdumpconfig

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints._
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.taskdumpconfig.{CreateTaskDumpConfigReq, TaskDumpConfigResp, UpdateTaskDumpConfigReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TaskDumpConfigEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "task-dump-config"

  val saveEndpoint: TgSecuredEndpoint[CreateTaskDumpConfigReq, TaskDumpConfigResp] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[CreateTaskDumpConfigReq])
      .out(jsonBody[TgJsonSuccess[TaskDumpConfigResp]])

  val findByIdEndpoint: TgSecuredEndpoint[UUID, TaskDumpConfigResp] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TaskDumpConfigResp]])

  val updateEndpoint: TgSecuredEndpoint[UpdateTaskDumpConfigReq, TaskDumpConfigResp] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[UpdateTaskDumpConfigReq])
      .out(jsonBody[TgJsonSuccess[TaskDumpConfigResp]])

  val deleteByIdEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

  val findByProjectIdEndpoint: TgSecuredEndpoint[UUID, Seq[TaskDumpConfigResp]] =
    securedEndpoint.endpoint.get
      .in(basePath / "project" / path[UUID]("id"))
      .out(jsonBody[TgJsonSuccess[Seq[TaskDumpConfigResp]]])

}
