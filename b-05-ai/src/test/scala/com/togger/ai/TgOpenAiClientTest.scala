package com.togger.ai

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.either._
import com.togger.ai.TgAiError._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.StreamBackend
import sttp.client4.impl.cats.CatsMonadAsyncError
import sttp.client4.testing.StreamBackendStub
import sttp.model.StatusCode
import sttp.tapir.Schema

class TgOpenAiClientTest extends AsyncWordSpec with Matchers {
  private val apiKey = TgAiApiKey("test-key")
  private val request = TgAiChatRequest(TgAiModel("gpt-4o-mini"), List(TgAiMessage.User("hello")))

  final case class Greeting(text: String)
  private implicit val greetingCodec: Codec[Greeting] = deriveCodec
  private implicit val greetingSchema: Schema[Greeting] = Schema.derived

  private def completionJson(content: String): String = {
    val escaped = io.circe.Json.fromString(content).noSpaces
    s"""{"id":"c1","choices":[{"message":{"role":"assistant","content":$escaped,"refusal":null},"index":0,
       |"finish_reason":"stop"}],"created":1,"model":"gpt-4o-mini","object":"chat.completion",
       |"usage":{"prompt_tokens":3,"completion_tokens":4,"total_tokens":7}}""".stripMargin
  }

  private val emptyChoicesJson =
    """{"id":"c1","choices":[],"created":1,"model":"gpt-4o-mini","object":"chat.completion",
      |"usage":{"prompt_tokens":3,"completion_tokens":0,"total_tokens":3}}""".stripMargin

  private def clientReplying(status: StatusCode, body: String): TgOpenAiClient = {
    val stub: StreamBackend[IO, Fs2Streams[IO]] =
      StreamBackendStub[IO, Fs2Streams[IO]](new CatsMonadAsyncError[IO]).whenAnyRequest
        .thenRespondAdjust(body, status)
    new TgOpenAiClient(stub)
  }

  private def errorJson(message: String): String =
    s"""{"error":{"message":"$message","type":"invalid_request_error","param":null,"code":null}}"""

  "TgOpenAiClient.chat" should {
    "return text and usage on success" in {
      clientReplying(StatusCode.Ok, completionJson("hi there"))
        .chat(apiKey, request)
        .map(_ shouldBe TgAiChatResult("hi there", Some(TgAiUsage(3, 4, 7))).asRight)
        .unsafeToFuture()
    }

    "return TgAiEmptyResponse when there are no choices" in {
      clientReplying(StatusCode.Ok, emptyChoicesJson)
        .chat(apiKey, request)
        .map(_ shouldBe TgAiEmptyResponse.asLeft)
        .unsafeToFuture()
    }

    "map 401 to TgAiAuthentication" in {
      clientReplying(StatusCode.Unauthorized, errorJson("bad key"))
        .chat(apiKey, request)
        .map(_ shouldBe TgAiAuthentication.asLeft)
        .unsafeToFuture()
    }

    "map 429 to TgAiRateLimited" in {
      clientReplying(StatusCode.TooManyRequests, errorJson("slow down"))
        .chat(apiKey, request)
        .map(_ shouldBe TgAiRateLimited.asLeft)
        .unsafeToFuture()
    }

    "map 400 to TgAiInvalidRequest" in {
      clientReplying(StatusCode.BadRequest, errorJson("bad model"))
        .chat(apiKey, request)
        .map(_.left.toOption.exists(_.isInstanceOf[TgAiInvalidRequest]) shouldBe true)
        .unsafeToFuture()
    }

    "map 503 to TgAiProviderUnavailable" in {
      clientReplying(StatusCode.ServiceUnavailable, errorJson("down"))
        .chat(apiKey, request)
        .map(_.left.toOption.exists(_.isInstanceOf[TgAiProviderUnavailable]) shouldBe true)
        .unsafeToFuture()
    }
  }

  "TgOpenAiClient with a custom base uri" should {
    "call the chat completions path of that server" in {
      val requestedUri = new java.util.concurrent.atomic.AtomicReference[String]()
      val stub: StreamBackend[IO, Fs2Streams[IO]] =
        StreamBackendStub[IO, Fs2Streams[IO]](new CatsMonadAsyncError[IO]).whenAnyRequest
          .thenRespondF { request =>
            IO { requestedUri.set(request.uri.toString); sttp.client4.testing.ResponseStub.adjust(completionJson("hi")) }
          }
      new TgOpenAiClient(stub, TgOllama.BaseUri)
        .chat(TgOllama.ApiKey, request)
        .map(result => (result.isRight, requestedUri.get()) shouldBe (true, "http://localhost:11434/v1/chat/completions"))
        .unsafeToFuture()
    }
  }

  "TgOpenAiClient.chatAs" should {
    "decode a structured response" in {
      clientReplying(StatusCode.Ok, completionJson("""{"text":"hello"}"""))
        .chatAs[Greeting](apiKey, request)
        .map(_ shouldBe Greeting("hello").asRight)
        .unsafeToFuture()
    }

    "return TgAiResponseParseFailed for malformed JSON" in {
      clientReplying(StatusCode.Ok, completionJson("not json"))
        .chatAs[Greeting](apiKey, request)
        .map(_.left.toOption.exists(_.isInstanceOf[TgAiResponseParseFailed]) shouldBe true)
        .unsafeToFuture()
    }
  }

  "TgOpenAiClient.chatStream" should {
    "emit non-empty deltas and skip empty ones" in {
      def chunk(content: String) =
        s"""data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"gpt-4o-mini",
           |"choices":[{"index":0,"delta":{"content":"$content"},"finish_reason":null}]}""".stripMargin.replace("\n", "") + "\n\n"
      val sse = chunk("Hel") + chunk("") + chunk("lo") + "data: [DONE]\n\n"
      val stub: StreamBackend[IO, Fs2Streams[IO]] =
        StreamBackendStub[IO, Fs2Streams[IO]](new CatsMonadAsyncError[IO]).whenAnyRequest
          .thenRespondAdjust(fs2.Stream.emits(sse.getBytes("UTF-8")).covary[IO])
      new TgOpenAiClient(stub)
        .chatStream(apiKey, request)
        .flatMap(_.traverse(_.compile.toList))
        .map(_ shouldBe List(TgAiChunk("Hel"), TgAiChunk("lo")).asRight)
        .unsafeToFuture()
    }

    "map an error status to a TgAiError" in {
      clientReplying(StatusCode.TooManyRequests, errorJson("slow down"))
        .chatStream(apiKey, request)
        .map(_.isLeft shouldBe true)
        .unsafeToFuture()
    }
  }
}
