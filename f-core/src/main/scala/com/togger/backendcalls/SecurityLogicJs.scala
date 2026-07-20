package com.togger.backendcalls

import cats.effect.IO
import com.togger.auth.{AuthAccount, SecurityLogic}
import com.togger.common.{TgIds, TgJsonFailure, TgJsonSuccess}
import com.togger.logging.TgLogging

class SecurityLogicJs extends SecurityLogic with TgLogging {

  override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
    TgJsonSuccess.justBodyRightIO(
      AuthAccount(
        id = TgIds.UNASSIGNED_UUID,
        firstName = "n/a",
        lastName = "n/a",
        email = "n/a",
        createdAt = null,
        updatedAt = null,
        deviceInfo = "n/a",
        sessionCreatedAt = null,
        sessionUpdatedAt = null
      )
    )

}
