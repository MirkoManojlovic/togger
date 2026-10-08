package com.togger.milestone

import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class MilestoneDateRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val milestoneDateRepo = new MilestoneDateRepoImpl

  def milestoneDate(milestoneId: UUID) = MilestoneDate(
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
        savedMilestoneDate <- milestoneDateRepo.insert(milestoneDate(savedMilestone.id))
        foundMilestoneDate1 <- milestoneDateRepo.findById(savedMilestoneDate.id)
        _ <- milestoneDateRepo.deleteById(foundMilestoneDate1.id)
        foundMilestoneDate2 <- milestoneDateRepo.findById(savedMilestoneDate.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedMilestoneDate shouldBe foundMilestoneDate1)
        cp.reportAll()
        foundMilestoneDate2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by milestone id" in { transactor =>
      val res = for {
        savedMilestone <- createMilestone()
        milestoneDatesToInsert = (0 to 10).map(_ => milestoneDate(savedMilestone.id))
        savedMilestoneDates <- milestoneDatesToInsert.map(milestoneDateRepo.insert).toList.sequence
        foundMilestoneDates <- milestoneDateRepo
          .findPaginatedByMilestoneId(savedMilestone.id, DbPageRequest.default)
          .map(_.items)
      } yield foundMilestoneDates shouldBe savedMilestoneDates

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
        milestoneDatesForMilestone1 = (0 to 10).map(_ => milestoneDate(savedMilestone1.id))
        milestoneDatesForMilestone2 = (0 to 5).map(_ => milestoneDate(savedMilestone2.id))
        savedMilestoneDates1 <- milestoneDatesForMilestone1.map(milestoneDateRepo.insert).toList.sequence
        _ <- milestoneDatesForMilestone2.map(milestoneDateRepo.insert).toList.sequence
        foundMilestoneDates <- milestoneDateRepo
          .findPaginatedByProjectId(savedMilestone1.projectId, DbPageRequest.default)
          .map(_.items)
      } yield foundMilestoneDates.toSet shouldBe savedMilestoneDates1.toSet

      res.transact(transactor)
    }

  }

}
