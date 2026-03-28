package com.togger.auth

import cats.effect.IO
import com.togger.common.{TgJsonFailure, TgJsonSuccess}

trait SecurityLogic {
  def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]]
}
