package com.togger.ai

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.either._
import com.togger.ai.TgAiError._
import io.circe.Decoder
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import sttp.tapir.Schema

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class TgAiServiceTest extends AsyncWordSpec with Matchers {
  private val accountId = UUID.randomUUID()
  private val validRequest = TgAiChatRequest(TgAiModel("gpt-4o-mini"), List(TgAiMessage.User("hello")))
  private val chatResult = TgAiChatResult("hi", None)

  private class StubClient(reply: Either[TgAiError, TgAiChatResult]) extends TgAiClient {
    val calls = new AtomicInteger(0)
    val lastKey = new java.util.concurrent.atomic.AtomicReference[TgAiApiKey]()

    override def listModels(apiKey: TgAiApiKey): IO[Either[TgAiError, List[TgAiModel]]] =
      IO { calls.incrementAndGet(); lastKey.set(apiKey); List(TgAiModel("llama3")).asRight }

    override def chat(apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, TgAiChatResult]] =
      IO { calls.incrementAndGet(); lastKey.set(apiKey); reply }

    override def chatAs[T: Schema: Decoder](apiKey: TgAiApiKey, request: TgAiChatRequest): IO[Either[TgAiError, T]] =
      IO(calls.incrementAndGet()) *> IO.pure(TgAiEmptyResponse.asLeft)

    override def chatStream(
        apiKey: TgAiApiKey,
        request: TgAiChatRequest
    ): IO[Either[TgAiError, fs2.Stream[IO, TgAiChunk]]] =
      IO(calls.incrementAndGet()) *> IO.pure(fs2.Stream.emit(TgAiChunk("a")).covary[IO].asRight)
  }

  private def keyProvider(key: Either[TgAiError, TgAiApiKey]) = new TgAiApiKeyProvider {
    override def apiKey(accountId: UUID): IO[Either[TgAiError, TgAiApiKey]] = IO.pure(key)
  }

  "TgAiService" should {
    "list the models with the resolved api key" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service.listModels(accountId).map { result =>
        (result, client.lastKey.get()) shouldBe (List(TgAiModel("llama3")).asRight, TgAiApiKey("secret"))
      }.unsafeToFuture()
    }

    "not list models without an api key" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, new TgNoApiKeyProvider)
      service.listModels(accountId).map { result =>
        (result, client.calls.get()) shouldBe (TgAiApiKeyMissing.asLeft, 0)
      }.unsafeToFuture()
    }

    "pass the resolved api key to the client and return its result" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service.chat(accountId, validRequest).map { result =>
        (result, client.lastKey.get()) shouldBe (chatResult.asRight, TgAiApiKey("secret"))
      }.unsafeToFuture()
    }

    "return TgAiApiKeyMissing without calling the client when no key is available" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, new TgNoApiKeyProvider)
      service.chat(accountId, validRequest).map { result =>
        (result, client.calls.get()) shouldBe (TgAiApiKeyMissing.asLeft, 0)
      }.unsafeToFuture()
    }

    "reject a request without messages" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service.chat(accountId, validRequest.copy(messages = Nil)).map { result =>
        (result.left.toOption.exists(_.isInstanceOf[TgAiInvalidRequest]), client.calls.get()) shouldBe (true, 0)
      }.unsafeToFuture()
    }

    "reject a blank model" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service.chat(accountId, validRequest.copy(model = TgAiModel(" "))).map { result =>
        (result.left.toOption.exists(_.isInstanceOf[TgAiInvalidRequest]), client.calls.get()) shouldBe (true, 0)
      }.unsafeToFuture()
    }

    "pass through client errors" in {
      val client = new StubClient(TgAiRateLimited.asLeft)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service.chat(accountId, validRequest).map(_ shouldBe TgAiRateLimited.asLeft).unsafeToFuture()
    }

    "stream chunks from the client" in {
      val client = new StubClient(chatResult.asRight)
      val service = new TgAiService(client, keyProvider(TgAiApiKey("secret").asRight))
      service
        .chatStream(accountId, validRequest)
        .flatMap(_.traverse(_.compile.toList))
        .map(_ shouldBe List(TgAiChunk("a")).asRight)
        .unsafeToFuture()
    }
  }
}
