package com.togger.milestone

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val milestoneSummaryRepo = new MilestoneSummaryRepoImpl
  val milestoneSummaryVersionRepo = new MilestoneSummaryVersionRepoImpl

  val v1 = "v1"
  val v2 = "v2"

  def milestoneSummaryVersion(milestoneSummaryId: UUID, suffix: String) = MilestoneSummaryVersion(
    version = UUID.randomUUID(),
    milestoneSummaryId = milestoneSummaryId,
    summary = s"Summary $suffix",
    startDate = LocalDate.of(2026, 1, 5),
    endDate = LocalDate.of(2026, 1, 9),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createMilestoneSummary() =
    for {
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      savedMilestone <- milestoneRepo.insert(
        Milestone(id = UUID.randomUUID(), projectId = savedProject.id, createdAt = nowAsInstant)
      )
      savedMilestoneSummary <- milestoneSummaryRepo.insert(
        MilestoneSummary(id = UUID.randomUUID(), milestoneId = savedMilestone.id, createdAt = nowAsInstant)
      )
    } yield savedMilestoneSummary

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedMilestoneSummary <- createMilestoneSummary()
        savedVersion <- milestoneSummaryVersionRepo.insert(milestoneSummaryVersion(savedMilestoneSummary.id, v1))
        foundVersion1 <- milestoneSummaryVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- milestoneSummaryVersionRepo.updateContent(savedVersion.copy(summary = "new summary"))
        foundVersion2 <- milestoneSummaryVersionRepo.findByVersion(savedVersion.version)
        _ <- milestoneSummaryVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- milestoneSummaryVersionRepo.findByVersion(savedVersion.version).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedVersion shouldBe foundVersion1)
        cp(updatedVersion shouldBe foundVersion2)
        cp(updatedVersion.summary shouldBe "new summary")
        cp.reportAll()
        foundVersion3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by milestone summary id, return newest first, and find the latest" in { transactor =>
      val res = for {
        savedMilestoneSummary <- createMilestoneSummary()
        milestoneSummaryVersion1 <- milestoneSummaryVersionRepo.insert(
          milestoneSummaryVersion(savedMilestoneSummary.id, v1)
        )
        milestoneSummaryVersion2 <- milestoneSummaryVersionRepo.insert(
          milestoneSummaryVersion(savedMilestoneSummary.id, v2)
        )
        milestoneSummaryVersion3 <- milestoneSummaryVersionRepo.insert(
          milestoneSummaryVersion(savedMilestoneSummary.id, "v3")
        )
        foundVersions <- milestoneSummaryVersionRepo
          .findPaginatedByMilestoneSummaryId(savedMilestoneSummary.id, DbPageRequest.default)
          .map(_.items)
        latest <- milestoneSummaryVersionRepo.findLatestByMilestoneSummaryId(savedMilestoneSummary.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            milestoneSummaryVersion3,
            milestoneSummaryVersion2,
            milestoneSummaryVersion1
          )
        )
        cp.reportAll()
        latest shouldBe milestoneSummaryVersion3
      }

      res.transact(transactor)
    }

    "count versions for a milestone summary" in { transactor =>
      val res = for {
        savedMilestoneSummary <- createMilestoneSummary()
        _ <- milestoneSummaryVersionRepo.insert(milestoneSummaryVersion(savedMilestoneSummary.id, v1))
        _ <- milestoneSummaryVersionRepo.insert(milestoneSummaryVersion(savedMilestoneSummary.id, v2))
        count <- milestoneSummaryVersionRepo.countByMilestoneSummaryId(savedMilestoneSummary.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the milestone summary is deleted" in { transactor =>
      val res = for {
        savedMilestoneSummary <- createMilestoneSummary()
        _ <- milestoneSummaryVersionRepo.insert(milestoneSummaryVersion(savedMilestoneSummary.id, v1))
        _ <- milestoneSummaryVersionRepo.insert(milestoneSummaryVersion(savedMilestoneSummary.id, v2))
        _ <- milestoneSummaryRepo.deleteById(savedMilestoneSummary.id)
        remainingVersions <- milestoneSummaryVersionRepo
          .findPaginatedByMilestoneSummaryId(savedMilestoneSummary.id, DbPageRequest.default)
          .map(_.items)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

  }

}
