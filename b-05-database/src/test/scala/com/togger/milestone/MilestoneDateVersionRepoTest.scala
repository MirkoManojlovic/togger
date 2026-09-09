package com.togger.milestone

import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneDateVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val milestoneDateRepo = new MilestoneDateRepoImpl
  val milestoneDateVersionRepo = new MilestoneDateVersionRepoImpl

  val v1 = "v1"
  val v2 = "v2"

  def milestoneDateVersion(milestoneDateId: UUID, suffix: String) = MilestoneDateVersion(
    version = UUID.randomUUID(),
    milestoneDateId = milestoneDateId,
    name = s"Milestone date $suffix",
    description = s"Description $suffix",
    date = nowAsInstant,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createMilestoneDate() =
    for {
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      savedMilestone <- milestoneRepo.insert(
        Milestone(id = UUID.randomUUID(), projectId = savedProject.id, createdAt = nowAsInstant)
      )
      savedMilestoneDate <- milestoneDateRepo.insert(
        MilestoneDate(id = UUID.randomUUID(), milestoneId = savedMilestone.id, createdAt = nowAsInstant)
      )
    } yield savedMilestoneDate

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedMilestoneDate <- createMilestoneDate()
        savedVersion <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v1))
        foundVersion1 <- milestoneDateVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- milestoneDateVersionRepo.updateContent(savedVersion.copy(description = "new description"))
        foundVersion2 <- milestoneDateVersionRepo.findByVersion(savedVersion.version)
        _ <- milestoneDateVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- milestoneDateVersionRepo.findByVersion(savedVersion.version).attempt
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

    "find by milestone date id, return newest first, and find the latest" in { transactor =>
      val res = for {
        savedMilestoneDate <- createMilestoneDate()
        milestoneDateVersion1 <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v1))
        milestoneDateVersion2 <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v2))
        milestoneDateVersion3 <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, "v3"))
        foundVersions <- milestoneDateVersionRepo.findByMilestoneDateId(savedMilestoneDate.id)
        latest <- milestoneDateVersionRepo.findLatestByMilestoneDateId(savedMilestoneDate.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            milestoneDateVersion3,
            milestoneDateVersion2,
            milestoneDateVersion1
          )
        )
        cp.reportAll()
        latest shouldBe milestoneDateVersion3
      }

      res.transact(transactor)
    }

    "count versions for a milestone date" in { transactor =>
      val res = for {
        savedMilestoneDate <- createMilestoneDate()
        _ <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v1))
        _ <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v2))
        count <- milestoneDateVersionRepo.countByMilestoneDateId(savedMilestoneDate.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the milestone date is deleted" in { transactor =>
      val res = for {
        savedMilestoneDate <- createMilestoneDate()
        _ <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v1))
        _ <- milestoneDateVersionRepo.insert(milestoneDateVersion(savedMilestoneDate.id, v2))
        _ <- milestoneDateRepo.deleteById(savedMilestoneDate.id)
        remainingVersions <- milestoneDateVersionRepo.findByMilestoneDateId(savedMilestoneDate.id)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

  }

}
