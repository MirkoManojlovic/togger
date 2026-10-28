package com.togger.controllers.ai

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.ai.TgAiError.{TgAiError, TgAiProviderUnavailable}
import com.togger.ai._
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common.{TgJsonFailure, TgJsonSuccess}
import com.togger.dto.TgAiModelDto
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.Schema
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.Instant
import java.util.UUID

class TgAiModelControllerTest extends AnyWordSpec with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")
  private val monad = new CatsMonadError[IO]()
  private val accountId = UUID.randomUUID()

  private val securityLogic = new SecurityLogic {
    override def securityLogic(token: String): IO[Either[TgJsonFailure, TgJsonSuccess[AuthAccount]]] =
      IO.pure(
        Right(
          TgJsonSuccess.justBody(
            AuthAccount(UUID.fromString(token), "John", "Doe", "john@email.com", now, now, "device", now, now)
          )
        )
      )
  }

  private class StubClient(models: Either[TgAiError, List[TgAiModel]]) extends TgAiClient {
    override def listModels(apiKey: TgAiApiKey): IO[Either[TgAiError, List[TgAiModel]]] = IO.pure(models)
    override def chat(apiKey: TgAiApiKey, request: TgAiChatRequest) = IO.never
    override def chatAs[T: Schema: io.circe.Decoder](apiKey: TgAiApiKey, request: TgAiChatRequest) = IO.never
    override def chatStream(apiKey: TgAiApiKey, request: TgAiChatRequest) = IO.never
  }

  private def controller(models: Either[TgAiError, List[TgAiModel]]) = {
    val keys = new TgStaticApiKeyProvider(TgAiApiKey("key"))
    new TgAiModelController(
      new TgAiService(new StubClient(models), keys),
      new TgAiModelEndpoints(new SecuredEndpoint(securityLogic))
    )
  }

  private def call(models: Either[TgAiError, List[TgAiModel]]) = {
    val c = controller(models)
    (for {
      principal <- c.list.securityLogic(monad)(accountId.toString)
      result <- c.list.logic(monad)(principal.toOption.get)(())
    } yield result).unsafeRunSync()
  }

  "TgAiModelController" should {
    "return the available models" in {
      call(List(TgAiModel("llama3"), TgAiModel("qwen3")).asRight).toOption.get.body.get shouldBe
        List(TgAiModelDto("llama3"), TgAiModelDto("qwen3"))
    }

    "answer a bad request when the AI provider is unavailable" in {
      call(TgAiProviderUnavailable("down").asLeft).isLeft shouldBe true
    }
  }
}
