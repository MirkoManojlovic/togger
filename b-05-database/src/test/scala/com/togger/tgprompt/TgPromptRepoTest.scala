package com.togger.tgprompt

import cats.implicits._
import com.togger.account.{AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, PaginationTestSupport, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class TgPromptRepoTest extends BusinessTgDbSpec
    with PaginationTestSupport
    with TgAccount
    with TimeHelpers {

  private val promptRepo = new TgPromptRepoImpl
  private val versionRepo = new TgPromptVersionRepoImpl
  private val otherAccountRepo = new AccountRepoImpl

  private def newPrompt(createdBy: UUID) = TgPrompt(UUID.randomUUID(), createdBy, nowAsInstant)

  private def newVersion(promptId: UUID, title: String, prompt: String) =
    TgPromptVersion(UUID.randomUUID(), promptId, title, prompt, nowAsInstant)

  private def insertWithVersion(prompt: TgPrompt, title: String, text: String) =
    for {
      saved <- promptRepo.insert(prompt)
      version <- versionRepo.insert(newVersion(saved.id, title, text))
    } yield saved -> version

  testNameFromClassName should {

    "insert, find and delete a prompt together with its versions" in { transactor =>
      (for {
        account <- createAccount
        saved <- promptRepo.insert(newPrompt(account.id))
        found <- promptRepo.findById(saved.id)
        v1 <- versionRepo.insert(newVersion(saved.id, "title", "text"))
        deleted <- promptRepo.deleteById(saved.id)
        versionsLeft <- versionRepo.countByTgPromptId(saved.id)
        afterDelete <- promptRepo.findById(saved.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(found shouldBe saved)
        cp(v1.tgPromptId shouldBe saved.id)
        cp(deleted shouldBe 1)
        cp(versionsLeft shouldBe 0)
        cp(afterDelete.isLeft shouldBe true)
        cp.reportAll()
        succeed
      }).transact(transactor)
    }

    "find the full prompt with its latest version" in { transactor =>
      (for {
        account <- createAccount
        saved <- promptRepo.insert(newPrompt(account.id))
        _ <- versionRepo.insert(newVersion(saved.id, "first", "first text"))
        latest <- versionRepo.insert(newVersion(saved.id, "second", "second text"))
        full <- promptRepo.findFullById(saved.id)
        latestFound <- versionRepo.findLatestByTgPromptId(saved.id)
      } yield {
        full shouldBe TgPromptFull(saved, latest)
        latestFound shouldBe latest
      }).transact(transactor)
    }

    "delete only the requested version" in { transactor =>
      (for {
        account <- createAccount
        saved <- promptRepo.insert(newPrompt(account.id))
        first <- versionRepo.insert(newVersion(saved.id, "first", "a"))
        second <- versionRepo.insert(newVersion(saved.id, "second", "b"))
        deleted <- versionRepo.deleteByVersion(first.version)
        remaining <- versionRepo.countByTgPromptId(saved.id)
        latest <- versionRepo.findLatestByTgPromptId(saved.id)
      } yield {
        deleted shouldBe 1
        remaining shouldBe 1
        latest shouldBe second
      }).transact(transactor)
    }

    "split 11 versions of a prompt over 3 pages of 5, newest first" in { transactor =>
      (for {
        account <- createAccount
        saved <- promptRepo.insert(newPrompt(account.id))
        created <- (0 to 10).toList.traverse(index => versionRepo.insert(newVersion(saved.id, s"v$index", "text")))
        result <- assertElevenRowsOverThreePages(
          versionRepo.findPaginatedByTgPromptId(saved.id, _),
          created.reverse.map(_.version)
        )(_.version)
      } yield result).transact(transactor)
    }

    "split 11 prompts of an account over 3 pages of 5, newest first, leaving other accounts out" in { transactor =>
      (for {
        account <- createAccount
        otherAccount <- otherAccountRepo.insert(createAccount(account.accountTypeId).copy(email = "other@email.com"))
        created <- (0 to 10).toList.traverse(index => insertWithVersion(newPrompt(account.id), s"p$index", "t"))
        _ <- insertWithVersion(newPrompt(otherAccount.id), "other", "t")
        result <- assertElevenRowsOverThreePages(
          promptRepo.findPaginatedFullByCreatedBy(account.id, _),
          created.reverse.map(_._1.id)
        )(_.tgPrompt.id)
      } yield result).transact(transactor)
    }
  }
}
