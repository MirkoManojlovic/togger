package com.togger.ui.slacker

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority
import com.togger.ui.teamdaily.{TeamDailyEntryDraft, TeamDailyTaskPlan}

import java.time.LocalDate
import java.util.UUID

class SlackerPlanTest extends AsyncJsWordSpec {

  private val monday = LocalDate.of(2026, 10, 19)

  private def planOf(name: String, days: Seq[LocalDate], status: LogEntryStatus = LogEntryStatus.InProgress) =
    TeamDailyTaskPlan(
      UUID.randomUUID(),
      name,
      "▶️ In progress",
      days,
      status,
      TaskPriority.Medium,
      None,
      Seq.empty,
      monday,
      monday.plusDays(10)
    )

  private val days = Seq(monday, monday.plusDays(1), monday.plusDays(2))
  private val longer = planOf("A", days)
  private val shorter = planOf("B", Seq(monday))

  testNameFromClassName should {

    "choose every task when none was narrowed down" in { _ =>
      SlackerPlan.chosen(Seq(longer, shorter), None) shouldBe Seq(longer, shorter)
    }

    "keep only the chosen tasks" in { _ =>
      SlackerPlan.chosen(Seq(longer, shorter), Some(Set(shorter.taskId))) shouldBe Seq(shorter)
    }

    "walk the days of a task from today into the past by default" in { _ =>
      SlackerPlan.positions(Seq(longer), Set.empty) shouldBe
        SlackerPosition.ChooseTasks +: days.reverse.map(SlackerPosition.Fill(longer.taskId, _)) :+ SlackerPosition.Review
    }

    "walk the days of a task from the past when it was asked for" in { _ =>
      SlackerPlan.positions(Seq(longer, shorter), Set(longer.taskId)) shouldBe
        SlackerPosition.ChooseTasks +: (days.map(SlackerPosition.Fill(longer.taskId, _)) :+
          SlackerPosition.Fill(shorter.taskId, monday)) :+ SlackerPosition.Review
    }

    "start the first day as the last status of the task" in { _ =>
      val withStatus = planOf("S", days, LogEntryStatus.Blocked)
      SlackerPlan.defaultEntry(withStatus, Set.empty, days.last, Map.empty) shouldBe
        TeamDailyEntryDraft(LogEntryStatus.Blocked, "")
    }

    "start a day as the day walked before it" in { _ =>
      val entries = Map(SlackerEntryKey(longer.taskId, days.last) -> TeamDailyEntryDraft(LogEntryStatus.Paused, "x"))
      SlackerPlan.defaultEntry(longer, Set.empty, days(1), entries).status shouldBe LogEntryStatus.Paused
    }
  }
}
