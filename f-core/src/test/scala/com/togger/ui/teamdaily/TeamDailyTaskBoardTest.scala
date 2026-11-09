package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority

import java.time.LocalDate
import java.util.UUID

class TeamDailyTaskBoardTest extends AsyncJsWordSpec {

  private def task(name: String, status: String) = TeamDailyTaskPlan(
    taskId = UUID.randomUUID(),
    name = name,
    statusName = status,
    missingDays = Seq.empty,
    previousStatus = LogEntryStatus.InProgress,
    priority = TaskPriority.Medium,
    milestoneId = None,
    labels = Seq.empty,
    startDate = LocalDate.of(2026, 1, 1),
    dueDate = LocalDate.of(2026, 1, 2)
  )

  testNameFromClassName should {

    "put the tasks in the column of their status, by name, the closed statuses last" in { _ =>
      val columns = TeamDailyTaskBoard.byColumn(
        Seq(
          task("w", "🗑️ Won't do"),
          task("d", "✅ Done"),
          task("b", "🔴 Blocked"),
          task("z", "▶️ In progress"),
          task("a", "▶️ In progress")
        )
      )
      columns.map(_.map(_.name)) shouldBe
        Seq(Seq("a", "z"), Seq.empty, Seq.empty, Seq("b"), Seq("d"), Seq("w"))
    }

    "leave out the tasks of a status without a column" in { _ =>
      TeamDailyTaskBoard.byColumn(Seq(task("t", "Todo"))).flatten shouldBe empty
    }
  }
}
