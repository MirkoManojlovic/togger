package com.togger.controllers.tgaiaction

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.ai.TgAiEndpoints
import com.togger.auth.{AuthAccount, SecuredEndpoint, SecurityLogic}
import com.togger.common.{TgClockImpl, TgJsonFailure, TgJsonSuccess}
import com.togger.db.MockedTransactor
import com.togger.dto.TgAiEndpointDto
import com.togger.project.ProjectAccountRepo
import com.togger.tgaiaction._
import com.togger.tgprompttemplate.TgPromptTemplateRepo
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import sttp.tapir.integ.cats.effect.CatsMonadError

import java.time.Instant
import java.util.UUID

class TgAiActionControllerTest extends AnyWordSpec with MockFactory with Matchers {

  private val now = Instant.parse("2026-10-12T10:00:00Z")

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

  private val actionRepo = mock[TgAiActionRepo]
  private val clock = new TgClockImpl

  private val controller = {
    val transactor = new MockedTransactor {}.transactorMock
    val service = new TgAiActionService(
      actionRepo,
      mock[TgAiActionEndpointRepo],
      mock[TgAiActionDefaultRepo],
      mock[TgPromptTemplateRepo],
      mock[ProjectAccountRepo],
      transactor
    )
    new TgAiActionController(service, new TgAiActionEndpoints(new SecuredEndpoint(securityLogic)), clock)
  }

  private val monad = new CatsMonadError[IO]()
  private val accountId = UUID.randomUUID()

  "TgAiActionController" should {

    "list the AI endpoints of the enum" in {
      val result = (for {
        principal <- controller.listEndpoints.securityLogic(monad)(accountId.toString)
        result <- controller.listEndpoints.logic(monad)(principal.toOption.get)(())
      } yield result).unsafeRunSync()

      result.toOption.get.body.get shouldBe
        TgAiEndpoints.values.map(e => TgAiEndpointDto(e.value, e.endpointName)).toList
    }

    "answer a bad request when the action does not exist" in {
      val id = UUID.randomUUID()
      (actionRepo.findById _).expects(id).returns(doobie.free.connection.raiseError[TgAiAction](UnexpectedEnd))

      val result = (for {
        principal <- controller.findById.securityLogic(monad)(accountId.toString)
        result <- controller.findById.logic(monad)(principal.toOption.get)(id)
      } yield result).unsafeRunSync()

      result.isLeft shouldBe true
    }
  }
}
