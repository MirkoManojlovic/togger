package com.togger.tgprompt

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import com.togger.tgprompt.TgPromptError._
import doobie.ConnectionIO
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class TgPromptServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with TestLoggingJvm {

  private val promptRepo = mock[TgPromptRepo]
  private val versionRepo = mock[TgPromptVersionRepo]

  private val service = new TgPromptService(promptRepo, versionRepo, transactorMock)

  private val accountId = UUID.randomUUID()

  private def prompt(createdBy: UUID = accountId) = TgPrompt(UUID.randomUUID(), createdBy, nowAsInstant)

  private def version(promptId: UUID, title: String = "title", text: String = "text") =
    TgPromptVersion(UUID.randomUUID(), promptId, title, text, nowAsInstant)

  testNameFromClassName should {

    "create the prompt and its first version" in {
      val created = prompt()
      val first = version(created.id)
      val full = TgPromptFull(created, first)

      (promptRepo.insert _).expects(created).returns(created.pure[ConnectionIO])
      (versionRepo.insert _).expects(first).returns(first.pure[ConnectionIO])
      (promptRepo.findFullById _).expects(created.id).returns(full.pure[ConnectionIO])

      service.create(created, first).map(_ shouldBe full).unsafeToFuture()
    }

    "save a new version when the content changed" in {
      val existing = prompt()
      val current = version(existing.id)
      val saved = version(existing.id, "new title", "new text")
      val full = TgPromptFull(existing, saved)

      (promptRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.findLatestByTgPromptId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (versionRepo.insert _).expects(*).returns(saved.pure[ConnectionIO])
      (promptRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .saveNewVersion(accountId, existing.id, "new title", "new text", nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "not save a new version when the content is the same" in {
      val existing = prompt()
      val current = version(existing.id)
      val full = TgPromptFull(existing, current)

      (promptRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.findLatestByTgPromptId _).expects(existing.id).returns(current.pure[ConnectionIO])
      (promptRepo.findFullById _).expects(existing.id).returns(full.pure[ConnectionIO])

      service
        .saveNewVersion(accountId, existing.id, current.title, current.prompt, nowAsInstant)
        .map(_ shouldBe Right(full))
        .unsafeToFuture()
    }

    "report a prompt of another account as not found" in {
      val foreign = prompt(UUID.randomUUID())

      (promptRepo.findById _).expects(foreign.id).returns(foreign.pure[ConnectionIO])

      service.findById(accountId, foreign.id).map(_ shouldBe Left(TgPromptNotFound)).unsafeToFuture()
    }

    "report an unknown prompt as not found" in {
      val id = UUID.randomUUID()

      (promptRepo.findById _).expects(id).returns(UnexpectedEnd.raiseError[ConnectionIO, TgPrompt])

      service.findById(accountId, id).map(_ shouldBe Left(TgPromptNotFound)).unsafeToFuture()
    }

    "not delete a prompt of another account" in {
      val foreign = prompt(UUID.randomUUID())

      (promptRepo.findById _).expects(foreign.id).returns(foreign.pure[ConnectionIO])

      service.delete(accountId, foreign.id).map(_ shouldBe Left(TgPromptNotFound)).unsafeToFuture()
    }

    "delete an own prompt" in {
      val existing = prompt()

      (promptRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (promptRepo.deleteById _).expects(existing.id).returns(1.pure[ConnectionIO])

      service.delete(accountId, existing.id).map(_ shouldBe Right(1)).unsafeToFuture()
    }

    "delete a version while others remain" in {
      val existing = prompt()
      val toDelete = version(existing.id)

      (versionRepo.findByVersion _).expects(toDelete.version).returns(toDelete.pure[ConnectionIO])
      (promptRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.countByTgPromptId _).expects(existing.id).returns(2.pure[ConnectionIO])
      (versionRepo.deleteByVersion _).expects(toDelete.version).returns(1.pure[ConnectionIO])

      service.deleteVersion(accountId, toDelete.version).map(_ shouldBe Right(1)).unsafeToFuture()
    }

    "not delete the last remaining version" in {
      val existing = prompt()
      val only = version(existing.id)

      (versionRepo.findByVersion _).expects(only.version).returns(only.pure[ConnectionIO])
      (promptRepo.findById _).expects(existing.id).returns(existing.pure[ConnectionIO])
      (versionRepo.countByTgPromptId _).expects(existing.id).returns(1.pure[ConnectionIO])

      service
        .deleteVersion(accountId, only.version)
        .map(_ shouldBe Left(CannotDeleteLastVersion))
        .unsafeToFuture()
    }

    "not delete a version of a prompt of another account" in {
      val foreign = prompt(UUID.randomUUID())
      val foreignVersion = version(foreign.id)

      (versionRepo.findByVersion _).expects(foreignVersion.version).returns(foreignVersion.pure[ConnectionIO])
      (promptRepo.findById _).expects(foreign.id).returns(foreign.pure[ConnectionIO])

      service
        .deleteVersion(accountId, foreignVersion.version)
        .map(_ shouldBe Left(TgPromptNotFound))
        .unsafeToFuture()
    }

    "report an unknown version" in {
      val unknown = UUID.randomUUID()

      (versionRepo.findByVersion _).expects(unknown).returns(UnexpectedEnd.raiseError[ConnectionIO, TgPromptVersion])

      service.deleteVersion(accountId, unknown).map(_ shouldBe Left(VersionNotFound)).unsafeToFuture()
    }
  }
}
