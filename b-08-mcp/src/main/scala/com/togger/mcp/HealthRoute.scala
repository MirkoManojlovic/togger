package com.togger.mcp

import io.circe.Json
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** `GET /health`: public on purpose (never behind the api key), so the Togger desktop app can check which version is
  * running. The body has the shape of the backend's health answer: `{"body": {"app": "mcp", "version": "..."}}`.
  */
class HealthRoute(version: String):

  val body: String =
    Json
      .obj("body" -> Json.obj("app" -> Json.fromString(HealthRoute.AppName), "version" -> Json.fromString(version)))
      .noSpaces

  val endpoint: ServerEndpoint[Any, Identity] =
    sttp.tapir.endpoint.get.in("health").out(stringJsonBody).handleSuccess(_ => body)

object HealthRoute:
  val AppName = "mcp"
