package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.dto.SearchAccountDto
import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority

import java.time.LocalDate
import java.util.UUID

class TeamDailyPlanTest extends AsyncJsWordSpec {

  private def member(first: String) = SearchAccountDto(UUID.randomUUID(), first, "Test", s"$first@test", UUID.randomUUID())
  private def task(name: String, status: String) =
    TeamDailyTaskPlan(
      UUID.randomUUID(),
      name,
      status,
      Seq(LocalDate.of(2026, 10, 19)),
      LogEntryStatus.InProgress,
      TaskPriority.Medium,
      None,
      Seq.empty
    )

  private val john = member("John")
  private val mark = member("Mark")
  private val blocked = task("B", "🔴 Blocked")
  private val inProgressB = task("B", "▶️ In progress")
  private val inProgressA = task("A", "▶️ In progress")
  private val testing = task("T", "🔬 Testing")
  private val candidates = Seq(
    TeamDailyMemberPlan(john, Seq(inProgressB, blocked)),
    TeamDailyMemberPlan(mark, Seq(testing, inProgressA))
  )

  testNameFromClassName should {

    "group the tasks of a member by status in the order of the daily statuses" in { _ =>
      TeamDailyPlan.byStatus(Seq(blocked, testing, inProgressB)).map(_.statusName) shouldBe
        Seq("▶️ In progress", "🔬 Testing", "🔴 Blocked")
    }

    "sort the tasks of a status by name" in { _ =>
      TeamDailyPlan.byStatus(Seq(inProgressB, inProgressA)).head.tasks shouldBe Seq(inProgressA, inProgressB)
    }

    "choose every task when none was narrowed down" in { _ =>
      TeamDailyPlan.chosen(candidates, None) shouldBe candidates
    }

    "keep only the chosen tasks and drop members left without any" in { _ =>
      TeamDailyPlan.chosen(candidates, Some(Set(blocked.taskId))) shouldBe
        Seq(TeamDailyMemberPlan(john, Seq(blocked)))
    }

    "choose nothing for an empty selection" in { _ =>
      TeamDailyPlan.chosen(candidates, Some(Set.empty)) shouldBe Seq.empty
    }

    "walk through choosing members and tasks before the days" in { _ =>
      TeamDailyPlan.positions(Seq.empty) shouldBe
        Seq(TeamDailyPosition.ChooseMembers, TeamDailyPosition.ChooseTasks, TeamDailyPosition.Review)
    }
  }
}
