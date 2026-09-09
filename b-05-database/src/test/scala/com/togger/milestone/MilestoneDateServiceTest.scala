package com.togger.milestone

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.TimeHelpers
import com.togger.db.MockedTransactor
import com.togger.milestone.MilestoneDateError.{CannotDeleteLastVersion, CannotEditLatestVersion}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.UUID

class MilestoneDateServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val milestoneDateRepo: MilestoneDateRepo = mock[MilestoneDateRepo]
  val milestoneDateVersionRepo: MilestoneDateVersionRepo = mock[MilestoneDateVersionRepo]

  val service = new MilestoneDateService(milestoneDateRepo, milestoneDateVersionRepo, transactorMock)

  val milestoneId: UUID = UUID.randomUUID()

  def newMilestoneDate() = MilestoneDate(
    id = UUID.randomUUID(),
    milestoneId = milestoneId,
    createdAt = nowAsInstant
  )

  def newVersion(suffix: String) = MilestoneDateVersion(
    version = UUID.randomUUID(),
    milestoneDateId = UUID.randomUUID(),
    name = s"Milestone date $suffix",
    description = s"Description $suffix",
    date = nowAsInstant,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a milestone date with a single version" in {
      val milestoneDate = newMilestoneDate()
      val version = newVersion("v1").copy(milestoneDateId = milestoneDate.id)

      (milestoneDateRepo.insert _).expects(milestoneDate).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(version).returns(version.pure[ConnectionIO])
      (milestoneDateVersionRepo.findByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(List(version).pure[ConnectionIO])

      (for {
        result <- service.createMilestoneDate(milestoneDate, version)
        savedMilestoneDate = result.milestoneDate
        savedVersion = result.version
        versions <- service.listVersions(savedMilestoneDate.id)
      } yield {
        val cp = new Checkpoint
        cp(versions shouldBe List(savedVersion))
        cp.reportAll()
        savedVersion.milestoneDateId shouldBe savedMilestoneDate.id
      }).unsafeToFuture()
    }

    "save a new version on every save, keeping old ones and moving the latest" in {
      val milestoneDate = newMilestoneDate()
      val firstVersion = newVersion("v1").copy(milestoneDateId = milestoneDate.id)
      val secondVersion = newVersion("v2").copy(milestoneDateId = milestoneDate.id)

      (milestoneDateRepo.insert _).expects(milestoneDate).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneDateRepo.findById _).expects(milestoneDate.id).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneDateRepo.findByMilestoneId _).expects(milestoneId).returns(List(milestoneDate).pure[ConnectionIO])
      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(List(secondVersion, firstVersion).pure[ConnectionIO])

      (for {
        created <- service.createMilestoneDate(milestoneDate, firstVersion)
        savedMilestoneDate = created.milestoneDate
        secondResult <- service.saveNewVersion(savedMilestoneDate.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        current <- service.findByMilestoneId(milestoneId)
        allVersions <- service.listVersions(savedMilestoneDate.id)
      } yield {
        val cp = new Checkpoint
        cp(allVersions.map(_.version) shouldBe List(savedSecondVersion.version, firstVersion.version))
        cp(current.map(_.versions.head.version) shouldBe List(savedSecondVersion.version))
        cp.reportAll()
        current.map(_.milestoneDate.id) shouldBe List(savedMilestoneDate.id)
      }).unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val milestoneDate = newMilestoneDate()
      val firstVersion = newVersion("v1").copy(milestoneDateId = milestoneDate.id)
      val secondVersion = newVersion("v2").copy(milestoneDateId = milestoneDate.id)
      val editedFirstVersion = firstVersion.withNewContent("old name edit", "old description edit", nowAsInstant)

      (milestoneDateRepo.insert _).expects(milestoneDate).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneDateRepo.findById _).expects(milestoneDate.id).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findByVersion _)
        .expects(secondVersion.version)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.updateContent _)
        .expects(editedFirstVersion)
        .returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        created <- service.createMilestoneDate(milestoneDate, firstVersion)
        savedMilestoneDate = created.milestoneDate
        secondResult <- service.saveNewVersion(savedMilestoneDate.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        editLatestResult <- service.updateVersionContent(
          savedSecondVersion.version,
          "edited name",
          "edited description",
          nowAsInstant
        )
        editOldResult <- service.updateVersionContent(
          firstVersion.version,
          "old name edit",
          "old description edit",
          nowAsInstant
        )
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.description) shouldBe Right("old description edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val milestoneDate = newMilestoneDate()
      val firstVersion = newVersion("v1").copy(milestoneDateId = milestoneDate.id)
      val secondVersion = newVersion("v2").copy(milestoneDateId = milestoneDate.id)

      (milestoneDateRepo.insert _).expects(milestoneDate).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneDateRepo.findById _).expects(milestoneDate.id).returns(milestoneDate.pure[ConnectionIO])
      (milestoneDateVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])

      (milestoneDateVersionRepo.findByVersion _)
        .expects(secondVersion.version)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(secondVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.countByMilestoneDateId _).expects(milestoneDate.id).returns(2.pure[ConnectionIO])
      (milestoneDateVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(firstVersion.pure[ConnectionIO])

      (milestoneDateVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.findLatestByMilestoneDateId _)
        .expects(milestoneDate.id)
        .returns(firstVersion.pure[ConnectionIO])
      (milestoneDateVersionRepo.countByMilestoneDateId _).expects(milestoneDate.id).returns(1.pure[ConnectionIO])

      (for {
        created <- service.createMilestoneDate(milestoneDate, firstVersion)
        savedMilestoneDate = created.milestoneDate
        secondResult <- service.saveNewVersion(savedMilestoneDate.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        deleteLatestResult <- service.deleteVersion(savedSecondVersion.version)
        latestAfterDelete <- milestoneDateVersionRepo
          .findLatestByMilestoneDateId(savedMilestoneDate.id)
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
