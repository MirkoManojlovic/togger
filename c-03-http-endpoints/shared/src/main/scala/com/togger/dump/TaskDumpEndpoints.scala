package com.togger.dump

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{TaskDumpResp, TaskDumpStatusResp, TaskLoadReq, TaskLoadResp}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class TaskDumpEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "project" / "dump"

  val dumpEndpoint: TgSecuredEndpoint[UUID, TaskDumpResp] =
    securedEndpoint.endpoint.post
      .in(basePath / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[TaskDumpResp]])

  val checkStatusEndpoint: TgSecuredEndpoint[UUID, TaskDumpStatusResp] =
    securedEndpoint.endpoint.get
      .in(basePath / "status" / path[UUID]("project-id"))
      .out(jsonBody[TgJsonSuccess[TaskDumpStatusResp]])

  val loadEndpoint: TgSecuredEndpoint[(UUID, TaskLoadReq), TaskLoadResp] =
    securedEndpoint.endpoint.post
      .in(basePath / "load" / path[UUID]("project-id"))
      .in(jsonBody[TaskLoadReq])
      .out(jsonBody[TgJsonSuccess[TaskLoadResp]])

}
