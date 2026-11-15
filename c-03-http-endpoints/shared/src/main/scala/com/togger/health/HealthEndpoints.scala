package com.togger.health

import com.togger.auth.BaseEndpoint
import com.togger.common.TgEndpoints.TgPublicEndpoint
import com.togger.common.TgJsonSuccess
import com.togger.dto.HealthResp
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody

/** Public on purpose: the status page must work without a session. */
class HealthEndpoints {

  val health: TgPublicEndpoint[Unit, HealthResp] =
    BaseEndpoint.baseEndpoint.get
      .in("health")
      .out(jsonBody[TgJsonSuccess[HealthResp]])
}
