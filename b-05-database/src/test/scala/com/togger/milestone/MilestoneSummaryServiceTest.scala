package com.togger.milestone

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{DbPageRequest, PaginatedResponse, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.milestone.MilestoneSummaryError.{CannotDeleteLastVersion, CannotEditLatestVersion}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val milestoneSummaryRepo: MilestoneSummaryRepo = mock[MilestoneSummaryRepo]
  val milestoneSummaryVersionRepo: MilestoneSummaryVersionRepo = mock[MilestoneSummaryVersionRepo]

  val service = new MilestoneSummaryService(milestoneSummaryRepo, milestoneSummaryVersionRepo, transactorMock)

  val milestoneId: UUID = UUID.randomUUID()

  def newMilestoneSummary() = MilestoneSummary(
    id = UUID.randomUUID(),
    milestoneId = milestoneId,
    createdAt = nowAsInstant
  )

  def newVersion(suffix: String) = MilestoneSummaryVersion(
    version = UUID.randomUUID(),
    milestoneSummaryId = UUID.randomUUID(),
    summary = s"Summary $suffix",
    startDate = LocalDate.of(2026, 1, 5),
    endDate = LocalDate.of(2026, 1, 9),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a milestone summary with a single version" in {
      val milestoneSummary = newMilestoneSummary()
      val version = newVersion("v1").copy(milestoneSummaryId = milestoneSummary.id)

      (milestoneSummaryRepo.insert _).expects(milestoneSummary).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(version).returns(version.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findPaginatedByMilestoneSummaryId _)
        .expects(milestoneSummary.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(version), 1L).pure[ConnectionIO])

      (for {
        result <- service.createMilestoneSummary(milestoneSummary, version)
        savedMilestoneSummary = result.milestoneSummary
        savedVersion = result.version
        versions <- service.listVersions(savedMilestoneSummary.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(versions shouldBe List(savedVersion))
        cp.reportAll()
        savedVersion.milestoneSummaryId shouldBe savedMilestoneSummary.id
      }).unsafeToFuture()
    }

    "save a new version on every save, keeping old ones and moving the latest" in {
      val milestoneSummary = newMilestoneSummary()
      val firstVersion = newVersion("v1").copy(milestoneSummaryId = milestoneSummary.id)
      val secondVersion = newVersion("v2").copy(milestoneSummaryId = milestoneSummary.id)

      (milestoneSummaryRepo.insert _).expects(milestoneSummary).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryRepo.findById _).expects(milestoneSummary.id).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryRepo.findPaginatedByMilestoneId _)
        .expects(milestoneId, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(milestoneSummary), 1L).pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findPaginatedByMilestoneSummaryId _)
        .expects(milestoneSummary.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(secondVersion, firstVersion), 2L).pure[ConnectionIO])

      (for {
        created <- service.createMilestoneSummary(milestoneSummary, firstVersion)
        savedMilestoneSummary = created.milestoneSummary
        secondResult <- service.saveNewVersion(savedMilestoneSummary.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        current <- service.findPaginatedByMilestoneId(milestoneId, DbPageRequest.default).map(_.items)
        allVersions <- service.listVersions(savedMilestoneSummary.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(allVersions.map(_.version) shouldBe List(savedSecondVersion.version, firstVersion.version))
        cp(current.map(_.versions.head.version) shouldBe List(savedSecondVersion.version))
        cp.reportAll()
        current.map(_.milestoneSummary.id) shouldBe List(savedMilestoneSummary.id)
      }).unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val milestoneSummary = newMilestoneSummary()
      val firstVersion = newVersion("v1").copy(milestoneSummaryId = milestoneSummary.id)
      val secondVersion = newVersion("v2").copy(milestoneSummaryId = milestoneSummary.id)
      val editedFirstVersion =
        firstVersion.withNewContent("old summary edit", LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 16))

      (milestoneSummaryRepo.insert _).expects(milestoneSummary).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryRepo.findById _).expects(milestoneSummary.id).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findByVersion _)
        .expects(secondVersion.version)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findByVersion _)
        .expects(firstVersion.version)
        .returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.updateContent _)
        .expects(editedFirstVersion)
        .returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        created <- service.createMilestoneSummary(milestoneSummary, firstVersion)
        savedMilestoneSummary = created.milestoneSummary
        secondResult <- service.saveNewVersion(savedMilestoneSummary.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        editLatestResult <- service.updateVersionContent(
          savedSecondVersion.version,
          "edited summary",
          LocalDate.of(2026, 1, 12),
          LocalDate.of(2026, 1, 16)
        )
        editOldResult <- service.updateVersionContent(
          firstVersion.version,
          "old summary edit",
          LocalDate.of(2026, 1, 12),
          LocalDate.of(2026, 1, 16)
        )
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.summary) shouldBe Right("old summary edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val milestoneSummary = newMilestoneSummary()
      val firstVersion = newVersion("v1").copy(milestoneSummaryId = milestoneSummary.id)
      val secondVersion = newVersion("v2").copy(milestoneSummaryId = milestoneSummary.id)

      (milestoneSummaryRepo.insert _).expects(milestoneSummary).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryRepo.findById _).expects(milestoneSummary.id).returns(milestoneSummary.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])

      (milestoneSummaryVersionRepo.findByVersion _)
        .expects(secondVersion.version)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.countByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(2.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(firstVersion.pure[ConnectionIO])

      (milestoneSummaryVersionRepo.findByVersion _)
        .expects(firstVersion.version)
        .returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(firstVersion.pure[ConnectionIO])
      (milestoneSummaryVersionRepo.countByMilestoneSummaryId _)
        .expects(milestoneSummary.id)
        .returns(1.pure[ConnectionIO])

      (for {
        created <- service.createMilestoneSummary(milestoneSummary, firstVersion)
        savedMilestoneSummary = created.milestoneSummary
        secondResult <- service.saveNewVersion(savedMilestoneSummary.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        deleteLatestResult <- service.deleteVersion(savedSecondVersion.version)
        latestAfterDelete <- milestoneSummaryVersionRepo
          .findLatestByMilestoneSummaryId(savedMilestoneSummary.id)
          .transact(transactorMock)
        deleteLastResult <- service.deleteVersion(firstVersion.version)
      } yield {
        val cp = new Checkpoint
        cp(deleteLatestResult shouldBe Right(1))
        cp(latestAfterDelete shouldBe firstVersion)
        cp.reportAll()
        deleteLastResult shouldBe Left(CannotDeleteLastVersion)
      }).unsafeToFuture()
    }

  }

}
