package com.togger.milestone

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.TgProject
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneWithStepsServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val milestoneRepo = new MilestoneRepoImpl
  private val milestoneVersionRepo = new MilestoneVersionRepoImpl
  private val stepRepo = new MilestoneStepRepoImpl
  private val stepVersionRepo = new MilestoneStepVersionRepoImpl

  private def milestoneOf(projectId: UUID) = {
    val milestone = Milestone(UUID.randomUUID(), projectId, nowAsInstant)
    val version = MilestoneVersion(
      version = UUID.randomUUID(),
      milestoneId = milestone.id,
      name = "Milestone",
      description = "Description",
      startDate = LocalDate.of(2026, 1, 5),
      dueDate = LocalDate.of(2026, 1, 30),
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )
    milestone -> version
  }

  private def stepOf(milestoneId: UUID, name: String) = {
    val step = MilestoneStep(UUID.randomUUID(), milestoneId, nowAsInstant)
    step -> MilestoneStepVersion(
      version = UUID.randomUUID(),
      milestoneStepId = step.id,
      name = name,
      description = s"$name description",
      status = MilestoneStepStatus.Planned,
      position = 99,
      startDate = LocalDate.of(2026, 1, 5),
      dueDate = LocalDate.of(2026, 1, 9),
      endDate = None,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant,
      changeComment = ""
    )
  }

  testNameFromClassName should {

    "create the milestone with its steps in the given order, each Planned" in { transactor =>
      val service = new MilestoneWithStepsService(
        milestoneRepo,
        milestoneVersionRepo,
        stepRepo,
        stepVersionRepo,
        transactor
      )

      (for {
        orgAndProject <- createOrgAndProject.transact(transactor)
        pair = milestoneOf(orgAndProject._2.id)
        (milestone, version) = pair
        steps = List("Design", "Development", "Production").map(stepOf(milestone.id, _))
        created <- service.create(milestone, version, steps)
        stored <- stepVersionRepo.findLatestByMilestoneId(created._1.milestone.id).transact(transactor)
      } yield {
        created._2.map(step => step.version.name -> step.version.position) shouldBe
          List("Design" -> 0, "Development" -> 1, "Production" -> 2)
        created._2.map(_.milestoneStep.milestoneId).distinct shouldBe List(created._1.milestone.id)
        stored.map(_.status).distinct shouldBe List(MilestoneStepStatus.Planned)
        stored.sortBy(_.position).map(_.name) shouldBe List("Design", "Development", "Production")
      })
    }

    "create a milestone without steps" in { transactor =>
      val service = new MilestoneWithStepsService(
        milestoneRepo,
        milestoneVersionRepo,
        stepRepo,
        stepVersionRepo,
        transactor
      )

      (for {
        orgAndProject <- createOrgAndProject.transact(transactor)
        pair = milestoneOf(orgAndProject._2.id)
        (milestone, version) = pair
        created <- service.create(milestone, version, Nil)
      } yield created._2 shouldBe Nil)
    }

    "leave no milestone behind when a step cannot be stored" in { transactor =>
      val service = new MilestoneWithStepsService(
        milestoneRepo,
        milestoneVersionRepo,
        stepRepo,
        stepVersionRepo,
        transactor
      )

      (for {
        orgAndProject <- createOrgAndProject.transact(transactor)
        pair = milestoneOf(orgAndProject._2.id)
        (milestone, version) = pair
        // A name over 256 characters does not fit the column.
        steps = List(stepOf(milestone.id, "Design"), stepOf(milestone.id, "x" * 300))
        result <- service.create(milestone, version, steps).attempt
        found <- milestoneRepo.findById(milestone.id).transact(transactor).attempt
      } yield {
        result.isLeft shouldBe true
        found.isLeft shouldBe true
      })
    }
  }
}
