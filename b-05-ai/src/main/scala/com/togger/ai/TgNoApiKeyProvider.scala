package com.togger.ai

import cats.effect.IO
import cats.syntax.either._
import com.togger.ai.TgAiError.{TgAiApiKeyMissing, TgAiError}

import java.util.UUID

/** Interim provider until API keys are stored in the database. */
class TgNoApiKeyProvider extends TgAiApiKeyProvider {
  override def apiKey(accountId: UUID): IO[Either[TgAiError, TgAiApiKey]] =
    IO.pure(TgAiApiKeyMissing.asLeft)
}
