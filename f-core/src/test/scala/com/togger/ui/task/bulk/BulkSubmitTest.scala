package com.togger.ui.task.bulk

import com.togger.task.{BulkTaskValidation, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class BulkSubmitTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val createdBy = UUID.randomUUID()
  private val statusId = UUID.randomUUID()
  private val now = Instant.parse("2026-10-06T12:00:00Z")
  private val defaults =
    BulkTaskDefaults(TaskPriority.Medium, Some(statusId), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16))

  private def task(name: String) = BulkTask.withDefaults(UUID.randomUUID(), name, defaults)
  private def blocker(
    project: Option[UUID] = Some(projectId),
    tasks: Seq[BulkTask] = Seq(task("A")),
    issues: Boolean = false
  ) = BulkSubmit.blocker(project, tasks, issues)

  "BulkSubmit.blocker" should {
    "have nothing against a project with tasks" in {
      blocker() shouldBe None
    }

    "ask for a project first" in {
      blocker(project = None, tasks = Seq.empty, issues = true) shouldBe Some("Pick a project")
    }

    "ask to fix the parse issues before anything else about the tasks" in {
      blocker(tasks = Seq.empty, issues = true) shouldBe Some("Fix the lines marked above first")
    }

    "ask for at least one task" in {
      blocker(tasks = Seq.empty) shouldBe Some("Add at least one task")
    }

    "refuse more tasks than the backend takes" in {
      blocker(tasks = Seq.fill(BulkTaskValidation.MaxTasks + 1)(task("A"))) shouldBe
        Some(s"At most ${BulkTaskValidation.MaxTasks} tasks at once")
    }

    "refuse tasks without a status, which is a project with no statuses" in {
      blocker(tasks = Seq(task("A").copy(statusId = None))) shouldBe Some("The project has no task statuses")
    }
  }

  "BulkSubmit.request" should {
    "keep the order of the tasks and map their attributes" in {
      val (label1, label2) = (UUID.randomUUID(), UUID.randomUUID())
      val (assignee, milestone) = (UUID.randomUUID(), UUID.randomUUID())
      val full = task("B").copy(
        priority = TaskPriority.High,
        assigneeId = Some(assignee),
        milestoneId = Some(milestone),
        labelIds = Set(label1, label2)
      )
      val request = BulkSubmit.request(Some(projectId), Seq(task("A"), full), hasParseIssues = false, createdBy, now)
      val created = request.toOption.get.tasks.map(_.task)
      (
        created.map(_.name),
        created(1).priority,
        created(1).assignedTo,
        created(1).milestone,
        created(1).taskLabelIds.toSet,
        created.map(_.projectId).distinct,
        created.map(_.taskStatusId).distinct
      ) shouldBe
        (
          Seq("A", "B"),
          TaskPriority.High,
          Some(assignee),
          Some(milestone),
          Set(label1, label2),
          Seq(projectId),
          Seq(statusId)
        )
    }

    "carry the dates, the author and the time, and no links" in {
      val request =
        BulkSubmit.request(Some(projectId), Seq(task("A")), hasParseIssues = false, createdBy, now).toOption.get
      val only = request.tasks.head
      (only.task.startDate, only.task.dueDate, only.task.createdBy, only.task.createdAt, only.links) shouldBe
        (defaults.startDate, defaults.dueDate, createdBy, now, Seq.empty)
    }

    "give the blocker instead of a request" in {
      BulkSubmit.request(None, Seq(task("A")), hasParseIssues = false, createdBy, now) shouldBe Left("Pick a project")
    }
  }
}
