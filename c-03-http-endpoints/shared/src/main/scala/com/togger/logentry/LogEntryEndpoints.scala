package com.togger.logentry

import com.togger.auth.SecuredEndpoint
import com.togger.common.TgEndpoints.{TgSecuredEndpoint, TgSecuredEndpointEmptyBody}
import com.togger.common.TgJsonSuccess
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.dto.{CreateTaskAndLinksReq, FindTaskResp, LogEntryDto, LogEntryVersionDto, UpdateTaskReq}
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

import java.util.UUID

class LogEntryEndpoints(securedEndpoint: SecuredEndpoint) {

  private val basePath: EndpointInput[Unit] = "log-entry"

  val saveEndpoint: TgSecuredEndpoint[LogEntryDto, LogEntryDto] =
    securedEndpoint.endpoint.post
      .in(basePath)
      .in(jsonBody[LogEntryDto])
      .out(jsonBody[TgJsonSuccess[LogEntryDto]])

  val findByTaskId: TgSecuredEndpoint[UUID, List[LogEntryDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("task-id"))
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

  val listVersionsEndpoint: TgSecuredEndpoint[UUID, List[LogEntryVersionDto]] =
    securedEndpoint.endpoint.get
      .in(basePath / path[UUID]("id") / "version")
      .out(jsonBody[TgJsonSuccess[List[LogEntryVersionDto]]])

  val updateVersionEndpoint: TgSecuredEndpoint[LogEntryVersionDto, LogEntryVersionDto] =
    securedEndpoint.endpoint.put
      .in(basePath / "version")
      .in(jsonBody[LogEntryVersionDto])
      .out(jsonBody[TgJsonSuccess[LogEntryVersionDto]])

  val deleteVersionEndpoint: TgSecuredEndpointEmptyBody[UUID] =
    securedEndpoint.endpoint.delete
      .in(basePath / "version" / path[UUID]("version"))
      .out(jsonBody[TgJsonSuccess[TgEmptyBody]])

}
