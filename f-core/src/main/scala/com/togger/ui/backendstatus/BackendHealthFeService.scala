package com.togger.ui.backendstatus

import cats.effect.IO
import com.togger.backendcalls.UnprotectedBackendClient
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.TgJsonSuccess
import com.togger.dto.HealthResp
import com.togger.health.HealthEndpoints

/** The public health check of the backend; no session is needed, so it works on the status page when logged out. */
trait BackendHealthFeService {
  def health(): TgRespIO[HealthResp]
}

class BackendHealthFeServiceImpl(unprotectedBackendClient: UnprotectedBackendClient) extends BackendHealthFeService {
  private val endpoints = new HealthEndpoints

  override def health(): TgRespIO[HealthResp] = unprotectedBackendClient.httpCall(endpoints.health)(())
}

class BackendHealthFeServicePreview extends BackendHealthFeService {
  override def health(): TgRespIO[HealthResp] =
    IO.pure(TgJsonSuccess.justBodyRight(HealthResp("backend", BackendHealthFeServicePreview.Version)))
}

object BackendHealthFeServicePreview {
  val Version = "preview"
}
