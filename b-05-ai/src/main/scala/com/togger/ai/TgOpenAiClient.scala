package com.togger.ai

import cats.effect.IO
import cats.syntax.either._
import com.togger.ai.TgAiError._
import com.togger.logging.TgLogging
import io.circe.Decoder
import io.circe.parser.{decode => decodeJson}
import sttp.ai.openai.{OpenAI, OpenAIUris}
import sttp.ai.openai.OpenAIExceptions.OpenAIException
import sttp.ai.openai.OpenAIExceptions.OpenAIException._
import sttp.ai.openai.requests.completions.chat.ChatRequestBody.{ChatBody, ChatCompletionModel, ResponseFormat}
import sttp.ai.openai.requests.completions.chat.ChatRequestResponseData.ChatResponse
import sttp.ai.openai.requests.completions.chat.message.{Content, Message}
import sttp.ai.openai.streaming.fs2._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.model.Uri
import sttp.tapir.Schema

import scala.concurrent.duration.Duration

/** Talks to OpenAI, or to any OpenAI compatible server such as Ollama through `baseUri`. */
class TgOpenAiClient(
  backend: StreamBackend[IO, Fs2Streams[IO]],
  baseUri: Uri = OpenAIUris.OpenAIBaseUri,
  timeout: Duration = Duration.Inf
) extends TgAiClient
    with TgLogging {

  override def listModels(apiKey: TgAiApiKey): IO[Either[TgAiError, List[TgAiModel]]] =
    openAI(apiKey).getModels
      .send(backend)
      .map(_.body.leftMap(toTgAiError).map(_.data.map(model => TgAiModel(model.id)).toList))
      .handleError(unavailable)
      .flatTap(logOutcome("listModels"))

  override def chat(apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, TgAiChatResult]] =
    sendChat(apiKey, toChatBody(request)).map(_.flatMap(toChatResult))

  override def chatAs[T: Schema: Decoder](apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, T]] = {
    val responseFormat = ResponseFormat.JsonSchema.withTapirSchema[T](structuredOutputName)
    val body = toChatBody(request).copy(responseFormat = Some(responseFormat))
    sendChat(apiKey, body).map(_.flatMap(toChatResult).flatMap(parseStructured[T]))
  }

  override def chatStream(
      apiKey: TgAiApiKey,
      request: TgAiChatRequest
  ): IO[Either[TgAiError, fs2.Stream[IO, TgAiChunk]]] =
    openAI(apiKey)
      .createStreamedChatCompletion[IO](toChatBody(request))
      .send(backend)
      .map(_.body.leftMap(toTgAiError).map(_.map(_.choices.flatMap(_.delta.content).mkString).filter(_.nonEmpty).map(TgAiChunk)))
      .handleError(unavailable)
      .flatTap(logOutcome("chatStream"))

  private def sendChat(apiKey: TgAiApiKey, body: ChatBody): IO[Either[TgAiError, ChatResponse]] =
    openAI(apiKey)
      .createChatCompletion(body)
      .send(backend)
      .map(_.body.leftMap(toTgAiError))
      .handleError(unavailable)
      .flatTap(logOutcome("chat"))

  private def openAI(apiKey: TgAiApiKey): OpenAI = new OpenAI(apiKey.value, baseUri = baseUri, timeout = timeout)

  private def toChatBody(request: TgAiChatRequest): ChatBody =
    ChatBody(
      messages = request.messages.map(toMessage),
      model = ChatCompletionModel.CustomChatCompletionModel(request.model.id),
      maxCompletionTokens = request.maxOutputTokens
    )

  private def toMessage(message: TgAiMessage): Message = message match {
    case TgAiMessage.System(text)    => Message.System(text)
    case TgAiMessage.User(text)      => Message.User(Content.TextContent(text))
    case TgAiMessage.Assistant(text) => Message.Assistant(text)
  }

  private def toChatResult(response: ChatResponse): Either[TgAiError, TgAiChatResult] =
    response.choices.headOption
      .map(_.message.content)
      .filter(_.nonEmpty)
      .toRight[TgAiError](TgAiEmptyResponse)
      .map(text => TgAiChatResult(text, Some(toUsage(response))))

  private def toUsage(response: ChatResponse): TgAiUsage =
    TgAiUsage(response.usage.promptTokens, response.usage.completionTokens, response.usage.totalTokens)

  private def parseStructured[T: Decoder](result: TgAiChatResult): Either[TgAiError, T] =
    decodeJson[T](result.text).leftMap(error => TgAiResponseParseFailed(error.getMessage))

  private def toTgAiError(exception: OpenAIException): TgAiError = exception match {
    case _: AuthenticationException | _: PermissionException => TgAiAuthentication
    case _: RateLimitException                               => TgAiRateLimited
    case e: InvalidRequestException                          => TgAiInvalidRequest(e.getMessage)
    case e: ServiceUnavailableException                      => TgAiProviderUnavailable(e.getMessage)
    case e: DeserializationOpenAIException                   => TgAiResponseParseFailed(e.getMessage)
    case e                                                   => TgAiUnexpected(e.getMessage)
  }

  private def unavailable[A]: Throwable => Either[TgAiError, A] =
    error => TgAiProviderUnavailable(error.getClass.getSimpleName).asLeft

  private def logOutcome[A](operation: String)(result: Either[TgAiError, A]): IO[Unit] =
    result.fold(
      error => IO(logger.warn(s"AI $operation failed: ${error.message}")),
      _ => IO(logger.info(s"AI $operation succeeded"))
    )

  private val structuredOutputName = "tg_ai_structured_output"
}
