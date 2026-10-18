package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp}
import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.task.TaskPriority

import java.time.{Instant, LocalDate}
import java.util.UUID

class TeamDailyTaskBadgesTest extends AsyncJsWordSpec {

  private val dueDate = LocalDate.of(2026, 10, 30)
  private val milestone = FindMilestoneResp(
    UUID.randomUUID(),
    UUID.randomUUID(),
    Instant.EPOCH,
    UUID.randomUUID(),
    "Release 1",
    "",
    MilestoneStatus.InProgress,
    dueDate.minusDays(10),
    dueDate,
    None,
    Instant.EPOCH
  )

  private def label(name: String) = FindTaskLabelResp(UUID.randomUUID(), UUID.randomUUID(), name, "")

  private def task(milestoneId: Option[UUID], labels: Seq[FindTaskLabelResp]) =
    TeamDailyTaskPlan(
      UUID.randomUUID(),
      "Task",
      "▶️ In progress",
      Seq(dueDate),
      LogEntryStatus.InProgress,
      TaskPriority.Medium,
      milestoneId,
      labels
    )

  testNameFromClassName should {

    "show milestone, priority and labels by name in that order" in { _ =>
      val badges = TeamDailyTaskBadges.of(task(Some(milestone.id), Seq(label("b"), label("a"))), Seq(milestone))
      badges.map(_.text).take(2) shouldBe Seq("Release 1", TaskPriority.Medium.shortCode)
      badges.map(_.text).slice(2, 4) shouldBe Seq("a", "b")
      badges should have size 4
    }

    "leave out the milestone and labels a task does not have" in { _ =>
      TeamDailyTaskBadges.of(task(None, Seq.empty), Seq(milestone)) should have size 1
    }

    "leave out a milestone that is not known" in { _ =>
      TeamDailyTaskBadges.of(task(Some(UUID.randomUUID()), Seq.empty), Seq(milestone)) should have size 1
    }
  }
}
