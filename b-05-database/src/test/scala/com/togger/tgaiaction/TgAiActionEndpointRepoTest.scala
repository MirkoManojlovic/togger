package com.togger.tgaiaction

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.ai.TgAiEndpoints
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TgAiActionEndpointRepoTest extends BusinessTgDbSpec with TgAccount with TimeHelpers {

  private val actionRepo = new TgAiActionRepoImpl
  private val endpointRepo = new TgAiActionEndpointRepoImpl
  private val aiEndpointRepo = new com.togger.ai.AiEndpointRepoImpl

  private val endpointId = TgAiEndpoints.MilestoneSummary.value

  private def newAction(createdBy: UUID) =
    TgAiAction(UUID.randomUUID(), createdBy, "Summarise", "Summarise this", None, "gpt-5", nowAsInstant, nowAsInstant)

  private val seedEndpoint = aiEndpointRepo.insert(
    com.togger.ai.AiEndpointRow(endpointId, TgAiEndpoints.MilestoneSummary.endpointName)
  )

  testNameFromClassName should {

    "bind, rebind and find the endpoints of an action" in { transactor =>
      (for {
        account <- createAccount
        _ <- seedEndpoint
        _ <- aiEndpointRepo.insert(com.togger.ai.AiEndpointRow(99, "other"))
        action <- actionRepo.insert(newAction(account.id))
        _ <- endpointRepo.replaceBindings(action.id, List(endpointId, 99))
        both <- endpointRepo.findByActionIds(List(action.id))
        _ <- endpointRepo.replaceBindings(action.id, List(99))
        one <- endpointRepo.findByActionIds(List(action.id))
        bound <- endpointRepo.isBound(action.id, 99)
        unbound <- endpointRepo.isBound(action.id, endpointId)
      } yield {
        both.map(_.aiEndpointId) shouldBe List(endpointId, 99)
        one.map(_.aiEndpointId) shouldBe List(99)
        (bound, unbound) shouldBe (true, false)
      }).transact(transactor)
    }

    "delete the bindings together with the action" in { transactor =>
      (for {
        account <- createAccount
        _ <- seedEndpoint
        action <- actionRepo.insert(newAction(account.id))
        _ <- endpointRepo.replaceBindings(action.id, List(endpointId))
        _ <- actionRepo.deleteById(action.id)
        left <- endpointRepo.findByActionIds(List(action.id))
      } yield left shouldBe Nil).transact(transactor)
    }
  }
}
