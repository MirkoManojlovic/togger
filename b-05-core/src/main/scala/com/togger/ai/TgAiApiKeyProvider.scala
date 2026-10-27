package com.togger.ai

import cats.effect.IO
import com.togger.ai.TgAiError.TgAiError

import java.util.UUID

trait TgAiApiKeyProvider {
  def apiKey(accountId: UUID): IO[Either[TgAiError, TgAiApiKey]]
}
