package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.task.TaskPriority
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate
import java.util.UUID

class BulkSummaryTest extends AnyWordSpec with Matchers {

  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 12)
  private val defaults = BulkTaskDefaults.of(monday.minusDays(3), workDays, Seq.empty)

  private def task(name: String) = BulkTask.withDefaults(UUID.randomUUID(), name, defaults)

  "BulkSummary.of" should {
    "have nothing to show for no tasks" in {
      val summary = BulkSummary.of(Seq.empty, workDays)
      (summary.taskCount, summary.workDays, summary.earliestStart, summary.latestStart) shouldBe (0, 0, None, None)
    }

    "count tasks and add up their work days" in {
      val summary = BulkSummary.of(Seq(task("A"), task("B"), task("C")), workDays)
      (summary.taskCount, summary.workDays) shouldBe (3, 15)
    }

    "find the earliest and the latest start" in {
      val early = task("A")
      val late = task("B").copy(startDate = monday.plusDays(7), dueDate = monday.plusDays(11))
      val summary = BulkSummary.of(Seq(late, early), workDays)
      (summary.earliestStart, summary.latestStart) shouldBe (Some(monday), Some(monday.plusDays(7)))
    }

    "measure the calendar span up to the last due date, both ends included" in {
      val late = task("B").copy(startDate = monday.plusDays(7), dueDate = monday.plusDays(11))
      val summary = BulkSummary.of(Seq(task("A"), late), workDays)
      (summary.lastDueDate, summary.calendarSpanDays) shouldBe (Some(monday.plusDays(11)), Some(12))
    }

    "group by priority from the most urgent" in {
      val tasks = Seq(task("A"), task("B").copy(priority = TaskPriority.Critical), task("C"))
      BulkSummary.of(tasks, workDays).byPriority shouldBe Seq(TaskPriority.Critical -> 1, TaskPriority.Medium -> 2)
    }

    "group by status with the most used first" in {
      val (one, other) = (UUID.randomUUID(), UUID.randomUUID())
      val tasks = Seq(
        task("A").copy(statusId = Some(other)),
        task("B").copy(statusId = Some(one)),
        task("C").copy(statusId = Some(one))
      )
      BulkSummary.of(tasks, workDays).byStatus shouldBe Seq(Some(one) -> 2, Some(other) -> 1)
    }

    "count tasks without an assignee and the distinct labels in use" in {
      val (x, y) = (UUID.randomUUID(), UUID.randomUUID())
      val tasks = Seq(
        task("A").copy(assigneeId = Some(UUID.randomUUID()), labelIds = Set(x, y)),
        task("B").copy(labelIds = Set(x))
      )
      val summary = BulkSummary.of(tasks, workDays)
      (summary.unassigned, summary.labelsUsed) shouldBe (1, 2)
    }
  }
}
