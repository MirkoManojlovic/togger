package com.togger.preview

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.common.{BadRequest, PageRequest}
import com.togger.demo.{DemoIdentity, DemoMilestonesData, DemoTaskConfigData}
import com.togger.dto._
import com.togger.task.TaskPriority
import com.togger.ui.milestone.{MilestoneFeServicePreview, MilestoneStepFeServicePreview}
import com.togger.ui.task.TaskFeServicePreview

import java.time.{Instant, LocalDate}
import java.util.UUID

/** A task is in a step and the milestone of the task is the milestone of its step. */
class PreviewTaskStepsTest extends AsyncJsWordSpec {

  private val firstPage = PageRequest(1, 100)
  private val monday = LocalDate.parse("2026-06-15")

  private class Services(val env: PreviewTestEnv = new PreviewTestEnv) {
    private val m = env.module
    val tasks = new TaskFeServicePreview(m.context, m.projects, m.taskConfig, m.tasks)
    val steps = new MilestoneStepFeServicePreview(m.context, m.projects, m.milestones, m.milestoneSteps)
    val milestones = new MilestoneFeServicePreview(m.context, m.projects, m.milestones, m.milestoneSteps)
    env.loginAsDemo()

    val todoStatus: UUID = DemoTaskConfigData.statusId(0, 4)
    val doneStatus: UUID = DemoTaskConfigData.statusId(0, 6)

    def newTask(stepId: Option[UUID], project: UUID = DemoIdentity.ProjectId): CreateTaskAndLinksReq =
      CreateTaskAndLinksReq(
        CreateTaskReq(
          project,
          "In a step",
          "desc",
          None,
          DemoIdentity.AccountId,
          todoStatus,
          Instant.parse("2026-06-15T10:00:00Z"),
          Instant.parse("2026-06-15T10:00:00Z"),
          TaskPriority.Medium,
          stepId,
          monday.plusDays(1),
          monday.plusDays(3),
          Seq.empty
        ),
        Seq.empty
      )

    def stepsOf(milestoneId: UUID): scala.concurrent.Future[List[FindMilestoneStepResp]] =
      env.run(steps.findByMilestoneId(milestoneId)).map(_.toOption.get)
  }

  private def isBadRequest(result: Either[_, _]): Boolean = result.left.exists(_.isInstanceOf[BadRequest])

  testNameFromClassName should {

    "give a task the milestone of its step and the step" in { _ =>
      val s = new Services
      for {
        stepList <- s.stepsOf(DemoMilestonesData.BetaId)
        step = stepList.head
        created <- s.env.run(s.tasks.createTask(s.newTask(Some(step.id))))
        found <- s.env.run(s.tasks.findTaskById(created.toOption.get.task.id))
      } yield {
        created.map(r => (r.task.milestoneStepId, r.task.milestone)) shouldBe Right((Some(step.id), Some(DemoMilestonesData.BetaId)))
        found.map(r => (r.task.milestoneStepId, r.task.milestone)) shouldBe Right((Some(step.id), Some(DemoMilestonesData.BetaId)))
      }
    }

    "have no milestone for a task without a step" in { _ =>
      val s = new Services
      s.env.run(s.tasks.createTask(s.newTask(None))).map { created =>
        created.map(r => (r.task.milestoneStepId, r.task.milestone)) shouldBe Right((None, None))
      }
    }

    "reject a step of another project or one that does not exist" in { _ =>
      val s = new Services
      for {
        foreignSteps <- s.stepsOf(DemoMilestonesData.MobileMvpId)
        foreign <- s.env.run(s.tasks.createTask(s.newTask(Some(foreignSteps.head.id))))
        missing <- s.env.run(s.tasks.createTask(s.newTask(Some(UUID.randomUUID()))))
      } yield {
        isBadRequest(foreign) shouldBe true
        isBadRequest(missing) shouldBe true
      }
    }

    "filter the tasks by the milestone and by the step" in { _ =>
      val s = new Services
      def count(filter: TaskFilter) =
        s.env.run(s.tasks.findByProjectId(DemoIdentity.ProjectId, filter, firstPage)).map(_.toOption.get.items)
      for {
        betaSteps <- s.stepsOf(DemoMilestonesData.BetaId)
        _ <- s.env.run(s.tasks.createTask(s.newTask(Some(betaSteps.last.id))))
        inMilestone <- count(TaskFilter(milestoneIds = List(DemoMilestonesData.BetaId)))
        inStep <- count(TaskFilter(milestoneStepIds = List(betaSteps.last.id)))
        withoutStep <- count(TaskFilter(milestoneStepIds = List(com.togger.common.TgIds.NO_MILESTONE_UUID)))
        withoutMilestone <- count(TaskFilter(milestoneIds = List(com.togger.common.TgIds.NO_MILESTONE_UUID)))
      } yield {
        inMilestone.nonEmpty shouldBe true
        inMilestone.forall(_.milestone.contains(DemoMilestonesData.BetaId)) shouldBe true
        inStep.exists(_.name == "In a step") shouldBe true
        inStep.forall(_.milestoneStepId.contains(betaSteps.last.id)) shouldBe true
        withoutStep.forall(_.milestoneStepId.isEmpty) shouldBe true
        withoutMilestone.map(_.id).toSet shouldBe withoutStep.map(_.id).toSet
      }
    }

    "count the tasks in each step, and how many are done" in { _ =>
      val s = new Services
      for {
        before <- s.stepsOf(DemoMilestonesData.BetaId)
        target = before.head
        _ <- s.env.run(s.tasks.createTask(s.newTask(Some(target.id))))
        after <- s.stepsOf(DemoMilestonesData.BetaId)
      } yield {
        after.find(_.id == target.id).map(_.tasksTotal) shouldBe Some(target.tasksTotal + 1)
        after.find(_.id == target.id).map(_.tasksDone) shouldBe Some(target.tasksDone)
        after.map(_.tasksTotal).sum should be > 0
      }
    }

    "move a task to another step with an update" in { _ =>
      val s = new Services
      for {
        stepList <- s.stepsOf(DemoMilestonesData.BetaId)
        created <- s.env.run(s.tasks.createTask(s.newTask(Some(stepList.head.id))))
        task = created.toOption.get.task
        moved <- s.env.run(
          s.tasks.updateTask(
            UpdateTaskReq(
              UpdateTaskReq1(
                task.id, task.projectId, task.name, task.description, task.assignedTo, task.createdBy,
                task.taskStatusId, task.createdAt, task.updatedAt, task.priority, Some(stepList.last.id),
                Some(task.startDate), Some(task.dueDate), task.endDate, task.taskLabelIds, task.archived
              ),
              Seq.empty, Seq.empty, Seq.empty
            )
          )
        )
      } yield moved.map(r => (r.task.milestoneStepId, r.task.milestone)) shouldBe
        Right((Some(stepList.last.id), Some(DemoMilestonesData.BetaId)))
    }

    "refuse to delete a step that has tasks, and the last step of a milestone" in { _ =>
      val s = new Services
      for {
        stepList <- s.stepsOf(DemoMilestonesData.BetaId)
        withTasks = stepList.find(_.tasksTotal > 0).get
        refused <- s.env.runEmpty(s.steps.deleteMilestoneStepById(withTasks.id))
        empty = stepList.find(_.tasksTotal == 0)
        deleted <- empty.traverseOrNone(step => s.env.runEmpty(s.steps.deleteMilestoneStepById(step.id)))
      } yield {
        isBadRequest(refused) shouldBe true
        refused.left.exists(_.toString.contains("task")) shouldBe true
        deleted.foreach(_.isRight shouldBe true)
        succeed
      }
    }

    "refuse to delete the only step of a milestone" in { _ =>
      val s = new Services
      for {
        created <- s.env.run(
          s.milestones.createMilestone(
            CreateMilestoneReq(DemoIdentity.ProjectId, "Lonely", "", monday, monday.plusDays(11))
          )
        )
        only <- s.stepsOf(created.toOption.get.id)
        refused <- s.env.runEmpty(s.steps.deleteMilestoneStepById(only.head.id))
      } yield {
        only.map(_.name) shouldBe List("Main")
        isBadRequest(refused) shouldBe true
        refused.left.exists(_.toString.contains("last step")) shouldBe true
      }
    }

    "list the steps of every milestone of a project, and no others" in { _ =>
      val s = new Services
      for {
        all <- s.env.run(s.steps.findByProjectId(DemoIdentity.ProjectId))
        other <- s.env.run(s.steps.findByProjectId(DemoIdentity.SecondProjectId))
      } yield {
        val milestoneIds = all.toOption.get.map(_.milestoneId).toSet
        milestoneIds should contain(DemoMilestonesData.BetaId)
        milestoneIds should not contain DemoMilestonesData.MobileMvpId
        other.toOption.get.map(_.milestoneId).toSet should not contain DemoMilestonesData.BetaId
      }
    }

    "seed every demo task with a step of its own project and the milestone of that step" in { _ =>
      val s = new Services
      val m = s.env.module
      val seeded = m.tasks.tasks.all.flatMap(task => m.tasks.latestTaskVersion(task.id).map(task -> _))
      seeded.nonEmpty shouldBe true
      seeded.foreach { case (task, version) =>
        version.milestoneStepId.foreach { stepId =>
          val step = m.milestoneSteps.steps.find(stepId)
          step.map(_.milestoneId) shouldBe version.milestone
          step.flatMap(found => m.milestones.milestones.find(found.milestoneId)).map(_.projectId) shouldBe Some(task.projectId)
        }
        version.milestone.isDefined shouldBe version.milestoneStepId.isDefined
      }
      seeded.exists(_._2.milestoneStepId.isDefined) shouldBe true
      succeed
    }
  }

  private implicit class OptionToFuture[A](option: Option[A]) {
    def traverseOrNone[B](f: A => scala.concurrent.Future[B]): scala.concurrent.Future[Option[B]] =
      option.fold(scala.concurrent.Future.successful(Option.empty[B]))(a => f(a).map(Some(_)))
  }
}
