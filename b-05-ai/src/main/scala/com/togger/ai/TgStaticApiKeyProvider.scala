package com.togger.ai

import cats.effect.IO
import cats.syntax.either._
import com.togger.ai.TgAiError.TgAiError

import java.util.UUID

class TgStaticApiKeyProvider(key: TgAiApiKey) extends TgAiApiKeyProvider {
  override def apiKey(accountId: UUID): IO[Either[TgAiError, TgAiApiKey]] = IO.pure(key.asRight)
}
