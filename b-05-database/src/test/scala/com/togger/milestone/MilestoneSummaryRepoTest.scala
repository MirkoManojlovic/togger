package com.togger.milestone

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneSummaryRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val milestoneSummaryRepo = new MilestoneSummaryRepoImpl

  def milestoneSummary(milestoneId: UUID) = MilestoneSummary(
    id = UUID.randomUUID(),
    milestoneId = milestoneId,
    createdAt = nowAsInstant
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
        savedMilestoneSummary <- milestoneSummaryRepo.insert(milestoneSummary(savedMilestone.id))
        foundMilestoneSummary1 <- milestoneSummaryRepo.findById(savedMilestoneSummary.id)
        _ <- milestoneSummaryRepo.deleteById(foundMilestoneSummary1.id)
        foundMilestoneSummary2 <- milestoneSummaryRepo.findById(savedMilestoneSummary.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedMilestoneSummary shouldBe foundMilestoneSummary1)
        cp.reportAll()
        foundMilestoneSummary2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by milestone id" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        milestoneSummarysToInsert = (0 to 10).map(_ => milestoneSummary(savedMilestone.id))
        savedMilestoneSummarys <- milestoneSummarysToInsert.map(milestoneSummaryRepo.insert).toList.sequence
        foundMilestoneSummarys <- milestoneSummaryRepo
          .findPaginatedByMilestoneId(savedMilestone.id, DbPageRequest.default)
          .map(_.items)
      } yield foundMilestoneSummarys shouldBe savedMilestoneSummarys

      res.transact(transactor)
    }

    "find by project id" in { transactor =>
      val res = for {
        savedMilestone1 <- createMilestone()
        savedOrganization2 <- organizationRepo.insert(organization.copy(name = "Organization name 2"))
        savedProject2 <- projectRepo.insert(project.copy(organizationId = savedOrganization2.id))
        savedMilestone2 <- milestoneRepo.insert(
          Milestone(id = UUID.randomUUID(), projectId = savedProject2.id, createdAt = nowAsInstant)
        )
        milestoneSummarysForMilestone1 = (0 to 10).map(_ => milestoneSummary(savedMilestone1.id))
        milestoneSummarysForMilestone2 = (0 to 5).map(_ => milestoneSummary(savedMilestone2.id))
        savedMilestoneSummarys1 <- milestoneSummarysForMilestone1.map(milestoneSummaryRepo.insert).toList.sequence
        _ <- milestoneSummarysForMilestone2.map(milestoneSummaryRepo.insert).toList.sequence
        foundMilestoneSummarys <- milestoneSummaryRepo
          .findPaginatedByProjectId(savedMilestone1.projectId, DbPageRequest.default)
          .map(_.items)
      } yield foundMilestoneSummarys.toSet shouldBe savedMilestoneSummarys1.toSet

      res.transact(transactor)
    }

  }

}
