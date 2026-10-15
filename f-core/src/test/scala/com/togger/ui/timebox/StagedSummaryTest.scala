package com.togger.ui.timebox

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp, SearchAccountDto}
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class StagedSummaryTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 12)
  private val member = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val backlog = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Backlog.statusName, "")
  private val done = FindTaskStatusResp(UUID.randomUUID(), projectId, DefaultTaskStatus.Done.statusName, "")
  private val bug = FindTaskLabelResp(UUID.randomUUID(), projectId, "bug", "")
  private val feature = FindTaskLabelResp(UUID.randomUUID(), projectId, "feature", "")

  private def task(start: LocalDate = monday, due: LocalDate = monday.plusDays(4)) = FindTaskRespFull(
    id = UUID.randomUUID(),
    projectId = projectId,
    name = "A task",
    description = "",
    assignedTo = None,
    createdBy = member,
    closedBy = None,
    taskStatus = backlog,
    priority = TaskPriority.Medium,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
    latestVersion = UUID.randomUUID(),
    logEntries = Seq.empty,
    links = Seq.empty,
    startDate = start,
    dueDate = due
  )

  "StagedSummary.of" should {
    "have nothing to show for no tasks" in {
      val summary = StagedSummary.of(Seq.empty, workDays, None)
      (summary.taskCount, summary.workDays, summary.earliestStart, summary.calendarSpanDays) shouldBe
        (0, 0, None, None)
    }

    "count tasks and add up their work days" in {
      val summary = StagedSummary.of(Seq(task(), task(), task()), workDays, None)
      (summary.taskCount, summary.workDays) shouldBe (3, 15)
    }

    "find the date range the tasks cover" in {
      val summary = StagedSummary.of(Seq(task(), task(monday.plusDays(7), monday.plusDays(11))), workDays, None)
      (summary.earliestStart, summary.lastDueDate, summary.calendarSpanDays) shouldBe
        (Some(monday), Some(monday.plusDays(11)), Some(12))
    }

    "count tasks by priority, most urgent first, and by status" in {
      val tasks = Seq(
        task().copy(priority = TaskPriority.Low),
        task().copy(priority = TaskPriority.High, taskStatus = done),
        task().copy(priority = TaskPriority.High)
      )
      val summary = StagedSummary.of(tasks, workDays, None)
      summary.byPriority shouldBe Seq(TaskPriority.High -> 2, TaskPriority.Low -> 1)
      summary.byStatus.toMap shouldBe Map(backlog -> 2, done -> 1)
    }

    "count tasks without an assignee and by label" in {
      val tasks = Seq(
        task().copy(assignedTo = Some(member), taskLabels = Seq(bug, feature)),
        task().copy(taskLabels = Seq(bug))
      )
      val summary = StagedSummary.of(tasks, workDays, None)
      summary.unassigned shouldBe 1
      summary.byLabel shouldBe Seq(bug -> 2, feature -> 1)
    }

    "count the tasks that stick out of the timebox" in {
      val timebox = Some((monday, monday.plusDays(4)))
      val tasks = Seq(task(), task(monday.minusDays(1), monday.plusDays(2)), task(monday, monday.plusDays(5)))
      StagedSummary.of(tasks, workDays, timebox).outsideTimebox shouldBe 2
    }

    "count none outside while the timebox has no dates" in {
      StagedSummary.of(Seq(task()), workDays, None).outsideTimebox shouldBe 0
    }
  }
}
