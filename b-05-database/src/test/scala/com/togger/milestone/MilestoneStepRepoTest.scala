package com.togger.milestone

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class MilestoneStepRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  private val milestoneRepo = new MilestoneRepoImpl
  private val stepRepo = new MilestoneStepRepoImpl
  private val taskRepo = new TaskRepoImpl
  private val taskVersionRepo = new TaskVersionRepoImpl

  private def newMilestone(projectId: UUID) =
    milestoneRepo.insert(Milestone(UUID.randomUUID(), projectId, nowAsInstant))

  /** A task whose versions are in the given steps, oldest first; the last one is its current step. */
  private def newTask(
    projectId: UUID,
    creator: UUID,
    statusId: UUID,
    steps: List[Option[UUID]],
    done: Boolean = false
  ): ConnectionIO[Task] =
    for {
      task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, creator, nowAsInstant))
      _ <- steps.zipWithIndex.traverse { case (step, index) =>
        taskVersionRepo.insert(
          TaskVersion(
            version = UUID.randomUUID(),
            taskId = task.id,
            name = s"Task v$index",
            description = "",
            assignedTo = None,
            taskStatusId = statusId,
            milestoneStepId = step,
            startDate = LocalDate.of(2026, 10, 1),
            dueDate = LocalDate.of(2026, 10, 20),
            endDate = Option.when(done && index == steps.size - 1)(LocalDate.of(2026, 10, 10)),
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      }
    } yield task

  testNameFromClassName should {

    "find the milestone and the project of steps, leaving out steps that do not exist" in { transactor =>
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        milestone <- newMilestone(projectId)
        a <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "A")
        b <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "B", position = 1)
        unknown = UUID.randomUUID()
        milestones <- stepRepo.findMilestoneIdsByStepIds(List(a.id, b.id, unknown))
        projects <- stepRepo.findProjectIdsByStepIds(List(a.id, unknown))
        none <- stepRepo.findMilestoneIdsByStepIds(Nil)
      } yield {
        milestones shouldBe Map(a.id -> milestone.id, b.id -> milestone.id)
        projects shouldBe Map(a.id -> projectId)
        none shouldBe Map.empty
      }).transact(transactor)
    }

    "find the steps and their latest versions of every milestone of a project, and no others" in { transactor =>
      val versionRepo = new MilestoneStepVersionRepoImpl
      (for {
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        first <- newMilestone(projectId)
        second <- newMilestone(projectId)
        a <- MilestoneStepFixtures.insertStep(first.id, nowAsInstant, "A")
        b <- MilestoneStepFixtures.insertStep(second.id, nowAsInstant, "B")
        // a newer version of A: only that one is the latest
        aLatest <- versionRepo.insert(
          MilestoneStepVersion(
            UUID.randomUUID(), a.id, "A2", "", MilestoneStepStatus.Done, 0, LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 5), Some(LocalDate.of(2026, 10, 4)), nowAsInstant, nowAsInstant, "done"
          )
        )
        otherProject <- projectRepo.insert(
          project.copy(id = UUID.randomUUID(), organizationId = orgAndProject._1.id, name = "Other project")
        )
        foreign <- newMilestone(otherProject.id)
        _ <- MilestoneStepFixtures.insertStep(foreign.id, nowAsInstant, "Foreign")
        steps <- stepRepo.findAllByProjectId(projectId)
        versions <- versionRepo.findLatestByProjectId(projectId)
      } yield {
        steps.map(_.id).toSet shouldBe Set(a.id, b.id)
        versions.map(_.name).toSet shouldBe Set("A2", "B")
        versions.find(_.milestoneStepId == a.id) shouldBe Some(aLatest)
      }).transact(transactor)
    }

    "count the tasks of a step by their latest version, and how many are done" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        status <- createTaskStatus(projectId)
        milestone <- newMilestone(projectId)
        design <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "Design")
        build <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "Build", position = 1)
        empty <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "Empty", position = 2)
        _ <- newTask(projectId, account.id, status.id, List(Some(design.id)), done = true)
        _ <- newTask(projectId, account.id, status.id, List(Some(design.id)))
        // moved from design to build: it counts for build only
        _ <- newTask(projectId, account.id, status.id, List(Some(design.id), Some(build.id)))
        _ <- newTask(projectId, account.id, status.id, List(None))
        counts <- stepRepo.countTasksByStepIds(List(design.id, build.id, empty.id))
        inMilestone <- stepRepo.countTasksOfMilestone(milestone.id)
      } yield {
        counts shouldBe Map(
          design.id -> MilestoneStepTaskCounts(2, 1),
          build.id -> MilestoneStepTaskCounts(1, 0)
        )
        inMilestone shouldBe 3
      }).transact(transactor)
    }

    "clear the step of the older task versions when the step is deleted" in { transactor =>
      (for {
        account <- createAccount
        orgAndProject <- createOrgAndProject
        projectId = orgAndProject._2.id
        status <- createTaskStatus(projectId)
        milestone <- newMilestone(projectId)
        old <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "Old")
        current <- MilestoneStepFixtures.insertStep(milestone.id, nowAsInstant, "Current", position = 1)
        task <- newTask(projectId, account.id, status.id, List(Some(old.id), Some(current.id)))
        deleted <- stepRepo.deleteById(old.id)
        versions <- taskVersionRepo.findByTaskId(task.id)
      } yield {
        deleted shouldBe 1
        versions.map(_.milestoneStepId) shouldBe List(Some(current.id), None)
      }).transact(transactor)
    }
  }
}
