package com.togger.task

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import com.togger.task.LinkError.{CannotDeleteLastVersion, CannotEditLatestVersion}
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class LinkServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val linkVersionRepo: LinkVersionRepo = mock[LinkVersionRepo]

  val service = new LinkService(linkVersionRepo, transactorMock)

  val linkId: UUID = UUID.randomUUID()

  def newVersion(suffix: String) = LinkVersion(
    version = UUID.randomUUID(),
    linkId = linkId,
    name = s"name $suffix",
    url = s"url-$suffix",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "list versions for a link" in {
      val version = newVersion("v1")

      (linkVersionRepo.findByLinkId _).expects(linkId).returns(List(version).pure[ConnectionIO])

      service
        .listVersions(linkId)
        .map(versions => versions shouldBe List(version))
        .unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val firstVersion = newVersion("v1")
      val secondVersion = newVersion("v2")
      val editedFirstVersion = firstVersion.withNewContent("old name edit", "old url edit")

      (linkVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(linkId).returns(secondVersion.pure[ConnectionIO])
      (linkVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(linkId).returns(secondVersion.pure[ConnectionIO])
      (linkVersionRepo.updateContent _).expects(editedFirstVersion).returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        editLatestResult <- service.updateVersionContent(secondVersion.version, "edited name", "edited url")
        editOldResult <- service.updateVersionContent(firstVersion.version, "old name edit", "old url edit")
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.url) shouldBe Right("old url edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val firstVersion = newVersion("v1")
      val secondVersion = newVersion("v2")

      (linkVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(linkId).returns(secondVersion.pure[ConnectionIO])
      (linkVersionRepo.countByLinkId _).expects(linkId).returns(2.pure[ConnectionIO])
      (linkVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (linkVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(linkId).returns(firstVersion.pure[ConnectionIO])
      (linkVersionRepo.countByLinkId _).expects(linkId).returns(1.pure[ConnectionIO])

      (for {
        deleteLatestResult <- service.deleteVersion(secondVersion.version)
        deleteLastResult <- service.deleteVersion(firstVersion.version)
      } yield {
        val cp = new Checkpoint
        cp(deleteLatestResult shouldBe Right(1))
        cp.reportAll()
        deleteLastResult shouldBe Left(CannotDeleteLastVersion)
      }).unsafeToFuture()
    }

  }

}
