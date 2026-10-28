package com.togger.ai

import cats.effect.IO
import com.togger.ai.TgAiError.TgAiError
import io.circe.Decoder
import sttp.tapir.Schema

trait TgAiClient {
  def listModels(apiKey: TgAiApiKey): IO[Either[TgAiError, List[TgAiModel]]]

  def chat(apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, TgAiChatResult]]

  def chatAs[T: Schema: Decoder](apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, T]]

  def chatStream(apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, fs2.Stream[IO, TgAiChunk]]]
}
