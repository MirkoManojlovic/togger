package com.togger.milestone

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val milestoneVersionRepo = new MilestoneVersionRepoImpl

  val v1 = "v1"
  val v2 = "v2"

  def milestoneVersion(milestoneId: UUID, suffix: String) = MilestoneVersion(
    version = UUID.randomUUID(),
    milestoneId = milestoneId,
    name = s"Milestone $suffix",
    description = s"Description $suffix",
    startDate = LocalDate.now(),
    estimatedEndDate = LocalDate.now().plusMonths(1),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createMilestone() =
    for {
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      savedMilestone <- milestoneRepo.insert(
        Milestone(id = UUID.randomUUID(), projectId = savedProject.id, createdAt = nowAsInstant)
      )
    } yield savedMilestone

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        savedVersion <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v1))
        foundVersion1 <- milestoneVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- milestoneVersionRepo.updateContent(savedVersion.copy(description = "new description"))
        foundVersion2 <- milestoneVersionRepo.findByVersion(savedVersion.version)
        _ <- milestoneVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- milestoneVersionRepo.findByVersion(savedVersion.version).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedVersion shouldBe foundVersion1)
        cp(updatedVersion shouldBe foundVersion2)
        cp(updatedVersion.description shouldBe "new description")
        cp.reportAll()
        foundVersion3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by milestone id, return newest first, and find the latest" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        milestoneVersion1 <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v1))
        milestoneVersion2 <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v2))
        milestoneVersion3 <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, "v3"))
        foundVersions <- milestoneVersionRepo.findByMilestoneId(savedMilestone.id)
        latest <- milestoneVersionRepo.findLatestByMilestoneId(savedMilestone.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            milestoneVersion3,
            milestoneVersion2,
            milestoneVersion1
          )
        )
        cp.reportAll()
        latest shouldBe milestoneVersion3
      }

      res.transact(transactor)
    }

    "count versions for a milestone" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        _ <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v1))
        _ <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v2))
        count <- milestoneVersionRepo.countByMilestoneId(savedMilestone.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the milestone is deleted" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        _ <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v1))
        _ <- milestoneVersionRepo.insert(milestoneVersion(savedMilestone.id, v2))
        _ <- milestoneRepo.deleteById(savedMilestone.id)
        remainingVersions <- milestoneVersionRepo.findByMilestoneId(savedMilestone.id)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

  }

}
