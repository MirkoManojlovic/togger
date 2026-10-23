package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.dto.FindTaskStatusResp
import com.togger.task.{DefaultTaskStatus, TaskPriority}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

class BulkTaskDefaultsTest extends AnyWordSpec with Matchers {

  private val monFri = WorkDayCalendar.DefaultWorkDays
  private val monday = LocalDate.of(2026, 10, 5)
  private val projectId = UUID.randomUUID()

  private def status(defaultStatus: DefaultTaskStatus) =
    FindTaskStatusResp(UUID.randomUUID(), projectId, defaultStatus.statusName, defaultStatus.description, "")

  private val todo = status(DefaultTaskStatus.Todo)
  private val backlog = status(DefaultTaskStatus.Backlog)

  "BulkTaskDefaults.of" should {
    "start next Monday and end 5 work days later, on Friday" in {
      val defaults = BulkTaskDefaults.of(monday.plusDays(2), monFri, Seq(backlog))
      (defaults.startDate, defaults.dueDate, defaults.dueDate.getDayOfWeek) shouldBe
        (monday.plusDays(7), monday.plusDays(11), DayOfWeek.FRIDAY)
    }

    "never start today, even on a Monday" in {
      BulkTaskDefaults.of(monday, monFri, Seq(backlog)).startDate shouldBe monday.plusDays(7)
    }

    "use medium priority" in {
      BulkTaskDefaults.of(monday, monFri, Seq(backlog)).priority shouldBe TaskPriority.Medium
    }

    "pick the backlog status among others" in {
      BulkTaskDefaults.of(monday, monFri, Seq(todo, backlog)).statusId shouldBe Some(backlog.id)
    }

    "fall back to the first status without a backlog" in {
      BulkTaskDefaults.of(monday, monFri, Seq(todo)).statusId shouldBe Some(todo.id)
    }

    "have no status when the project has none" in {
      BulkTaskDefaults.of(monday, monFri, Seq.empty).statusId shouldBe None
    }

    "start on the first work day after next Monday when the project does not work Mondays" in {
      val tueToSat =
        Set(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
      val defaults = BulkTaskDefaults.of(monday, tueToSat, Seq(backlog))
      (defaults.startDate, defaults.dueDate) shouldBe (monday.plusDays(8), monday.plusDays(12))
    }
  }

  "BulkTask.withDefaults" should {
    "have no assignee, end date, labels or milestone" in {
      val task = BulkTask.withDefaults(UUID.randomUUID(), "A", BulkTaskDefaults.of(monday, monFri, Seq(backlog)))
      (task.endDate, task.assigneeId, task.labelIds, task.milestoneId) shouldBe (None, None, Set.empty, None)
    }
  }
}
