package com.togger.health

import cats.effect.IO
import com.togger.common.TgJsonSuccess
import com.togger.dto.HealthResp

class HealthController(version: String) {

  val endpoints = new HealthEndpoints

  val response: HealthResp = HealthResp(HealthController.AppName, version)

  val health =
    endpoints.health.serverLogic { _ => IO.pure(TgJsonSuccess.justBodyRight(response)) }
}

object HealthController {
  val AppName = "backend"
}
