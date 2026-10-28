package com.togger.tgaiaction

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.ai.{AiEndpointRepoImpl, AiEndpointRow, TgAiEndpoints}
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TgAiActionDefaultRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val actionRepo = new TgAiActionRepoImpl
  private val endpointRepo = new TgAiActionEndpointRepoImpl
  private val defaultRepo = new TgAiActionDefaultRepoImpl
  private val aiEndpointRepo = new AiEndpointRepoImpl

  private val endpointId = TgAiEndpoints.MilestoneSummary.value

  private def newAction(createdBy: UUID, name: String) =
    TgAiAction(UUID.randomUUID(), createdBy, name, "Summarise this", None, "gpt-5", nowAsInstant, nowAsInstant)

  private def boundAction(createdBy: UUID, name: String) =
    for {
      action <- actionRepo.insert(newAction(createdBy, name))
      _ <- endpointRepo.replaceBindings(action.id, List(endpointId))
    } yield action

  testNameFromClassName should {

    "upsert replaces the default and different projects hold different defaults" in { transactor =>
      (for {
        account <- createAccount
        _ <- aiEndpointRepo.insert(AiEndpointRow(endpointId, TgAiEndpoints.MilestoneSummary.endpointName))
        orgAndProject <- createOrgAndProject
        projectA = orgAndProject._2
        projectB <- projectRepo.insert(project.copy(organizationId = orgAndProject._1.id, name = "Other project"))
        first <- boundAction(account.id, "First")
        second <- boundAction(account.id, "Second")
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectA.id, endpointId, first.id))
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectA.id, endpointId, second.id))
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectB.id, endpointId, first.id))
        inA <- defaultRepo.find(account.id, projectA.id, endpointId)
        inB <- defaultRepo.find(account.id, projectB.id, endpointId)
        listed <- defaultRepo.findByProject(account.id, projectA.id)
      } yield {
        inA.map(_.tgAiActionId) shouldBe Some(second.id)
        inB.map(_.tgAiActionId) shouldBe Some(first.id)
        listed.size shouldBe 1
      }).transact(transactor)
    }

    "reject a default for an action that is not bound to the endpoint" in { transactor =>
      (for {
        account <- createAccount
        _ <- aiEndpointRepo.insert(AiEndpointRow(endpointId, TgAiEndpoints.MilestoneSummary.endpointName))
        orgAndProject <- createOrgAndProject
        projectA = orgAndProject._2
        unbound <- actionRepo.insert(newAction(account.id, "Unbound"))
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectA.id, endpointId, unbound.id))
      } yield ()).transact(transactor).attempt.map(_.isLeft shouldBe true)
    }

    "remove the default when the action is unbound or deleted, and on delete" in { transactor =>
      (for {
        account <- createAccount
        _ <- aiEndpointRepo.insert(AiEndpointRow(endpointId, TgAiEndpoints.MilestoneSummary.endpointName))
        orgAndProject <- createOrgAndProject
        projectA = orgAndProject._2
        action <- boundAction(account.id, "Bound")
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectA.id, endpointId, action.id))
        _ <- endpointRepo.replaceBindings(action.id, Nil)
        afterUnbind <- defaultRepo.find(account.id, projectA.id, endpointId)
        again <- boundAction(account.id, "Again")
        _ <- defaultRepo.upsert(TgAiActionDefault(account.id, projectA.id, endpointId, again.id))
        removed <- defaultRepo.delete(account.id, projectA.id, endpointId)
        afterDelete <- defaultRepo.find(account.id, projectA.id, endpointId)
      } yield {
        afterUnbind shouldBe None
        removed shouldBe 1
        afterDelete shouldBe None
      }).transact(transactor)
    }
  }
}
