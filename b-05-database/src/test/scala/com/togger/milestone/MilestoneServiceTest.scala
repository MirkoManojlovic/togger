package com.togger.milestone

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{DbPageRequest, PaginatedResponse, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.dto.MilestoneFilter
import com.togger.milestone.MilestoneError.{CannotDeleteLastVersion, CannotEditLatestVersion}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.LocalDate
import java.util.UUID

class MilestoneServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val milestoneRepo: MilestoneRepo = mock[MilestoneRepo]
  val milestoneVersionRepo: MilestoneVersionRepo = mock[MilestoneVersionRepo]

  val service = new MilestoneService(milestoneRepo, milestoneVersionRepo, transactorMock)

  val projectId: UUID = UUID.randomUUID()

  def newMilestone() = Milestone(
    id = UUID.randomUUID(),
    projectId = projectId,
    createdAt = nowAsInstant
  )

  def newVersion(suffix: String) = MilestoneVersion(
    version = UUID.randomUUID(),
    milestoneId = UUID.randomUUID(),
    name = s"Milestone $suffix",
    description = s"Description $suffix",
    startDate = LocalDate.now(),
    dueDate = LocalDate.now().plusMonths(1),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a milestone with a single version" in {
      val milestone = newMilestone()
      val version = newVersion("v1").copy(milestoneId = milestone.id)

      (milestoneRepo.insert _).expects(milestone).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(version).returns(version.pure[ConnectionIO])
      (milestoneVersionRepo.findPaginatedByMilestoneId _)
        .expects(milestone.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(version), 1L).pure[ConnectionIO])

      (for {
        result <- service.createMilestone(milestone, version)
        savedMilestone = result.milestone
        savedVersion = result.version
        versions <- service.listVersions(savedMilestone.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(versions shouldBe List(savedVersion))
        cp.reportAll()
        savedVersion.milestoneId shouldBe savedMilestone.id
      }).unsafeToFuture()
    }

    "save a new version on every save, keeping old ones and moving the latest" in {
      val milestone = newMilestone()
      val firstVersion = newVersion("v1").copy(milestoneId = milestone.id)
      val secondVersion = newVersion("v2").copy(milestoneId = milestone.id)

      (milestoneRepo.insert _).expects(milestone).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneRepo.findById _).expects(milestone.id).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneRepo.findPaginatedByProjectId _)
        .expects(projectId, MilestoneFilter.empty, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(milestone), 1L).pure[ConnectionIO])
      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findPaginatedByMilestoneId _)
        .expects(milestone.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(secondVersion, firstVersion), 2L).pure[ConnectionIO])

      (for {
        created <- service.createMilestone(milestone, firstVersion)
        savedMilestone = created.milestone
        secondResult <- service.saveNewVersion(savedMilestone.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        current <- service.findPaginatedByProjectId(projectId, MilestoneFilter.empty, DbPageRequest.default)
        allVersions <- service.listVersions(savedMilestone.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(allVersions.map(_.version) shouldBe List(savedSecondVersion.version, firstVersion.version))
        cp(current.items.map(_.versions.head.version) shouldBe List(savedSecondVersion.version))
        cp.reportAll()
        current.items.map(_.milestone.id) shouldBe List(savedMilestone.id)
      }).unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val milestone = newMilestone()
      val firstVersion = newVersion("v1").copy(milestoneId = milestone.id)
      val secondVersion = newVersion("v2").copy(milestoneId = milestone.id)
      val editedFirstVersion = firstVersion.withNewContent(
        "old name edit",
        "old description edit",
        startDate = firstVersion.startDate,
        dueDate = firstVersion.dueDate
      )

      (milestoneRepo.insert _).expects(milestone).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneRepo.findById _).expects(milestone.id).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.updateContent _).expects(editedFirstVersion).returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        created <- service.createMilestone(milestone, firstVersion)
        savedMilestone = created.milestone
        secondResult <- service.saveNewVersion(savedMilestone.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        editLatestResult <- service.updateVersionContent(
          savedSecondVersion.version,
          "edited name",
          "edited description",
          startDate = secondVersion.startDate,
          dueDate = secondVersion.dueDate
        )
        editOldResult <- service.updateVersionContent(
          firstVersion.version,
          "old name edit",
          "old description edit",
          startDate = firstVersion.startDate,
          dueDate = firstVersion.dueDate
        )
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.description) shouldBe Right("old description edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val milestone = newMilestone()
      val firstVersion = newVersion("v1").copy(milestoneId = milestone.id)
      val secondVersion = newVersion("v2").copy(milestoneId = milestone.id)

      (milestoneRepo.insert _).expects(milestone).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (milestoneRepo.findById _).expects(milestone.id).returns(milestone.pure[ConnectionIO])
      (milestoneVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])

      (milestoneVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(secondVersion.pure[ConnectionIO])
      (milestoneVersionRepo.countByMilestoneId _).expects(milestone.id).returns(2.pure[ConnectionIO])
      (milestoneVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(firstVersion.pure[ConnectionIO])

      (milestoneVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (milestoneVersionRepo.findLatestByMilestoneId _).expects(milestone.id).returns(firstVersion.pure[ConnectionIO])
      (milestoneVersionRepo.countByMilestoneId _).expects(milestone.id).returns(1.pure[ConnectionIO])

      (for {
        created <- service.createMilestone(milestone, firstVersion)
        savedMilestone = created.milestone
        secondResult <- service.saveNewVersion(savedMilestone.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        deleteLatestResult <- service.deleteVersion(savedSecondVersion.version)
        latestAfterDelete <- milestoneVersionRepo.findLatestByMilestoneId(savedMilestone.id).transact(transactorMock)
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
