package com.togger.tgaiaction

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, PaginationTestSupport, TimeHelpers}
import com.togger.tgprompttemplate.{TgPromptTemplate, TgPromptTemplateRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TgAiActionRepoTest extends BusinessTgDbSpec with PaginationTestSupport with TgAccount with TimeHelpers {

  private val actionRepo = new TgAiActionRepoImpl
  private val promptTemplateRepo = new TgPromptTemplateRepoImpl

  private def newAction(createdBy: UUID, name: String = "Summarise", templateId: Option[UUID] = None) =
    TgAiAction(UUID.randomUUID(), createdBy, name, "Summarise this", templateId, "gpt-5", nowAsInstant, nowAsInstant)

  testNameFromClassName should {

    "insert, find, update and delete an action" in { transactor =>
      (for {
        account <- createAccount
        saved <- actionRepo.insert(newAction(account.id))
        found <- actionRepo.findById(saved.id)
        updated <- actionRepo.update(saved.copy(name = "Renamed", prompt = "New", model = "gpt-4"))
        deleted <- actionRepo.deleteById(saved.id)
        gone <- actionRepo.findById(saved.id).attempt
      } yield {
        found shouldBe saved
        updated.copy(updatedAt = saved.updatedAt).name shouldBe "Renamed"
        updated.prompt shouldBe "New"
        updated.model shouldBe "gpt-4"
        deleted shouldBe 1
        gone.isLeft shouldBe true
      }).transact(transactor)
    }

    "check whether the name exists for the account, ignoring one action" in { transactor =>
      (for {
        account <- createAccount
        saved <- actionRepo.insert(newAction(account.id, "Same"))
        taken <- actionRepo.existsByName(account.id, "Same", None)
        ignoringSelf <- actionRepo.existsByName(account.id, "Same", Some(saved.id))
        otherName <- actionRepo.existsByName(account.id, "Other", None)
      } yield (taken, ignoringSelf, otherName) shouldBe (true, false, false)).transact(transactor)
    }

    "reject a duplicate name of the same account" in { transactor =>
      (for {
        account <- createAccount
        _ <- actionRepo.insert(newAction(account.id, "Same"))
        _ <- actionRepo.insert(newAction(account.id, "Same"))
      } yield ()).transact(transactor).attempt.map(_.isLeft shouldBe true)
    }

    "set the prompt template to null when the template is deleted" in { transactor =>
      (for {
        account <- createAccount
        template <- promptTemplateRepo.insert(TgPromptTemplate(UUID.randomUUID(), account.id, nowAsInstant))
        saved <- actionRepo.insert(newAction(account.id, templateId = Some(template.id)))
        _ <- promptTemplateRepo.deleteById(template.id)
        found <- actionRepo.findById(saved.id)
      } yield found.tgPromptTemplateId shouldBe None).transact(transactor)
    }
  }
}
