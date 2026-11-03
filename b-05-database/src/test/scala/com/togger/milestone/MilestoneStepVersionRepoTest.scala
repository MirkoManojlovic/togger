package com.togger.milestone

import com.togger.account.TgAccount
import com.togger.common.BusinessTgDbSpec
import com.togger.common.DbPageRequest
import com.togger.common.TimeHelpers
import com.togger.organization.TgProject
import doobie.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneStepVersionRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  val milestoneRepo = new MilestoneRepoImpl
  val stepRepo = new MilestoneStepRepoImpl
  val versionRepo = new MilestoneStepVersionRepoImpl

  def stepVersion(
    stepId: UUID,
    name: String,
    position: Int,
    status: MilestoneStepStatus = MilestoneStepStatus.Planned
  ) =
    MilestoneStepVersion(
      version = UUID.randomUUID(),
      milestoneStepId = stepId,
      name = name,
      description = s"Description $name",
      status = status,
      position = position,
      startDate = LocalDate.of(2026, 1, 5),
      dueDate = LocalDate.of(2026, 1, 9),
      endDate = None,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant,
      changeComment = ""
    )

  def createMilestone() =
    for {
      orgAndProject <- createOrgAndProject
      milestone <- milestoneRepo.insert(
        Milestone(id = UUID.randomUUID(), projectId = orgAndProject._2.id, createdAt = nowAsInstant)
      )
    } yield milestone

  def createMilestoneInProjectOf(milestone: Milestone) =
    milestoneRepo.insert(Milestone(id = UUID.randomUUID(), projectId = milestone.projectId, createdAt = nowAsInstant))

  def createStep(milestoneId: UUID) =
    stepRepo.insert(MilestoneStep(id = UUID.randomUUID(), milestoneId = milestoneId, createdAt = nowAsInstant))

  testNameFromClassName should {

    "insert and find a version with status, position and change comment" in { transactor =>
      val res = for {
        milestone <- createMilestone()
        step <- createStep(milestone.id)
        saved <- versionRepo.insert(
          stepVersion(step.id, "Design", 2, MilestoneStepStatus.Done).copy(
            endDate = Some(LocalDate.of(2026, 1, 8)),
            changeComment = "Closed"
          )
        )
        found <- versionRepo.findByVersion(saved.version)
      } yield {
        found shouldBe saved
        (found.status, found.position, found.endDate, found.changeComment) shouldBe
          ((MilestoneStepStatus.Done, 2, Some(LocalDate.of(2026, 1, 8)), "Closed"))
      }

      res.transact(transactor)
    }

    "page versions newest first and find the latest one" in { transactor =>
      val res = for {
        milestone <- createMilestone()
        step <- createStep(milestone.id)
        v1 <- versionRepo.insert(stepVersion(step.id, "v1", 0))
        v2 <- versionRepo.insert(stepVersion(step.id, "v2", 1))
        page <- versionRepo.findPaginatedByMilestoneStepId(step.id, DbPageRequest.default)
        latest <- versionRepo.findLatestByMilestoneStepId(step.id)
      } yield {
        page.items shouldBe List(v2, v1)
        latest shouldBe v2
      }

      res.transact(transactor)
    }

    "find only the latest version of every step of the milestone" in { transactor =>
      val res = for {
        milestone <- createMilestone()
        other <- createMilestoneInProjectOf(milestone)
        design <- createStep(milestone.id)
        development <- createStep(milestone.id)
        otherStep <- createStep(other.id)
        _ <- versionRepo.insert(stepVersion(design.id, "Design v1", 0))
        designLatest <- versionRepo.insert(stepVersion(design.id, "Design v2", 1))
        developmentOnly <- versionRepo.insert(stepVersion(development.id, "Development", 0))
        _ <- versionRepo.insert(stepVersion(otherStep.id, "Other", 0))
        latest <- versionRepo.findLatestByMilestoneId(milestone.id)
        steps <- stepRepo.findAllByMilestoneId(milestone.id)
      } yield {
        latest.toSet shouldBe Set(designLatest, developmentOnly)
        steps.map(_.id).toSet shouldBe Set(design.id, development.id)
      }

      res.transact(transactor)
    }

    "count the steps per milestone by their latest status" in { transactor =>
      val res = for {
        milestone <- createMilestone()
        empty <- createMilestoneInProjectOf(milestone)
        design <- createStep(milestone.id)
        development <- createStep(milestone.id)
        abandoned <- createStep(milestone.id)
        // Design was Done once but reopened: only the latest version counts.
        _ <- versionRepo.insert(stepVersion(design.id, "Design v1", 0, MilestoneStepStatus.Done))
        _ <- versionRepo.insert(stepVersion(design.id, "Design v2", 0, MilestoneStepStatus.InProgress))
        _ <- versionRepo.insert(stepVersion(development.id, "Development", 1, MilestoneStepStatus.Done))
        _ <- versionRepo.insert(stepVersion(abandoned.id, "Staging", 2, MilestoneStepStatus.Abandoned))
        counts <- versionRepo.countByMilestoneIds(List(milestone.id, empty.id))
        none <- versionRepo.countByMilestoneIds(Nil)
      } yield {
        counts shouldBe Map(milestone.id -> MilestoneStepCounts(3, 2))
        none shouldBe Map.empty
      }

      res.transact(transactor)
    }

    "delete the versions together with the step" in { transactor =>
      val res = for {
        milestone <- createMilestone()
        step <- createStep(milestone.id)
        saved <- versionRepo.insert(stepVersion(step.id, "Design", 0))
        _ <- stepRepo.deleteById(step.id)
        latest <- versionRepo.findLatestByMilestoneId(milestone.id)
      } yield {
        latest shouldBe Nil
        saved.milestoneStepId shouldBe step.id
      }

      res.transact(transactor)
    }
  }
}
