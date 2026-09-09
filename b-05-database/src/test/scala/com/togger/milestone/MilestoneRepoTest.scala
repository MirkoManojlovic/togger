package com.togger.milestone

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl

  def milestone(projectId: UUID) = Milestone(
    id = UUID.randomUUID(),
    projectId = projectId,
    createdAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedMilestone <- milestoneRepo.insert(milestone(savedProject.id))
        foundMilestone1 <- milestoneRepo.findById(savedMilestone.id)
        _ <- milestoneRepo.deleteById(foundMilestone1.id)
        foundMilestone2 <- milestoneRepo.findById(savedMilestone.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedMilestone shouldBe foundMilestone1)
        cp.reportAll()
        foundMilestone2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by project id" in { transactor =>
      val res = for {
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        milestonesToInsert = (0 to 10).map(_ => milestone(savedProject.id))
        savedMilestones <- milestonesToInsert.map(milestoneRepo.insert).toList.sequence
        foundMilestones <- milestoneRepo.findByProjectId(savedProject.id)
      } yield foundMilestones shouldBe savedMilestones

      res.transact(transactor)
    }

  }

}
