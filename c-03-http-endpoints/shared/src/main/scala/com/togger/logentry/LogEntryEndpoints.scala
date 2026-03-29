package com.togger.logentry

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.TgSecuredEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.{CreateTaskAndLinksReq, FindTaskResp, LogEntryDto, UpdateTaskReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

class LogEntryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "log-entry"

  val saveEndpoint: TgSecuredEndpoint[LogEntryDto, LogEntryDto] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[LogEntryDto])
      .out(jsonBody[TgJsonSuccess[LogEntryDto]])

  val findByTaskId: TgSecuredEndpoint[Long, List[LogEntryDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[Long]("task-id"))
      .out(jsonBody[TgJsonSuccess[List[LogEntryDto]]])
  //      .tag(topic)
  //      .name(createName(findById))
  //      .summary(createSummary(findById))
  //      .description(s"Find $topic using id")

  val updateEndpoint: TgSecuredEndpoint[LogEntryDto, LogEntryDto] =
    securedEndpoint.endpoint.put
      .in(basePath)
      .in(jsonBody[LogEntryDto])
      .out(jsonBody[TgJsonSuccess[LogEntryDto]])

}
