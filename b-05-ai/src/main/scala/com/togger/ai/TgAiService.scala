package com.togger.ai

import cats.data.EitherT
import cats.effect.IO
import cats.syntax.either._
import com.togger.ai.TgAiError._
import com.togger.logging.TgLogging
import io.circe.Decoder
import sttp.tapir.Schema

import java.util.UUID

class TgAiService(client: TgAiClient, apiKeyProvider: TgAiApiKeyProvider) extends TgLogging {
  type Result[A] = Either[TgAiError, A]

  def chat(accountId: UUID, request: TgAiChatRequest): IO[Result[TgAiChatResult]] =
    withValidRequest(accountId, request)(client.chat(_, request))

  def chatAs[T: Schema: Decoder](accountId: UUID, request: TgAiChatRequest): IO[Result[T]] =
    withValidRequest(accountId, request)(client.chatAs[T](_, request))

  def chatStream(accountId: UUID, request: TgAiChatRequest): IO[Result[fs2.Stream[IO, TgAiChunk]]] =
    withValidRequest(accountId, request)(client.chatStream(_, request))

  private def withValidRequest[A](accountId: UUID, request: TgAiChatRequest)(
      call: TgAiApiKey => IO[Result[A]]
  ): IO[Result[A]] =
    validate(request) match {
      case Left(error) => IO.pure(error.asLeft)
      case Right(_) =>
        logger.info(s"AI request for account $accountId, model ${request.model.id}, ${request.messages.size} messages")
        (for {
          apiKey <- EitherT(apiKeyProvider.apiKey(accountId))
          result <- EitherT(call(apiKey))
        } yield result).value
    }

  private def validate(request: TgAiChatRequest): Result[Unit] =
    if (request.model.id.isBlank) TgAiInvalidRequest("model must not be blank").asLeft
    else if (request.messages.isEmpty) TgAiInvalidRequest("at least one message is required").asLeft
    else if (request.messages.forall(_.text.isBlank)) TgAiInvalidRequest("messages must not be blank").asLeft
    else ().asRight
}
