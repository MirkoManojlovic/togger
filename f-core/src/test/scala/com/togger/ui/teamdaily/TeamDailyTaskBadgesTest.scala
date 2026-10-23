package com.togger.ui.teamdaily

import com.togger.common.WorkDayCalendar
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

  private def label(name: String) = FindTaskLabelResp(UUID.randomUUID(), UUID.randomUUID(), name, "", "")

  private def task(milestoneId: Option[UUID], labels: Seq[FindTaskLabelResp]) =
    TeamDailyTaskPlan(
      UUID.randomUUID(),
      "Task",
      "▶️ In progress",
      Seq(dueDate),
      LogEntryStatus.InProgress,
      TaskPriority.Medium,
      milestoneId,
      labels,
      dueDate.minusDays(10),
      dueDate
    )

  testNameFromClassName should {

    "show the labels by name" in { _ =>
      TeamDailyTaskBadges.labels(task(None, Seq(label("b"), label("a")))).map(_.text) shouldBe Seq("a", "b")
    }

    "have no label badge for a task without labels" in { _ =>
      TeamDailyTaskBadges.labels(task(None, Seq.empty)) shouldBe Seq.empty
    }

    "show the milestone before the priority" in { _ =>
      TeamDailyTaskBadges.milestoneAndPriority(task(Some(milestone.id), Seq.empty), Seq(milestone)).map(_.text) shouldBe
        Seq("Release 1", TaskPriority.Medium.shortCode)
    }

    "leave out a milestone that is not there or not known" in { _ =>
      val onlyPriority = Seq(TaskPriority.Medium.shortCode)
      TeamDailyTaskBadges.milestoneAndPriority(task(None, Seq.empty), Seq(milestone)).map(_.text) shouldBe onlyPriority
      TeamDailyTaskBadges
        .milestoneAndPriority(task(Some(UUID.randomUUID()), Seq.empty), Seq(milestone))
        .map(_.text) shouldBe onlyPriority
    }

    "show the start date, the due date and the work days left" in { _ =>
      val monday = LocalDate.of(2026, 10, 26)
      TeamDailyTaskBadges
        .dates(task(None, Seq.empty), monday, WorkDayCalendar.DefaultWorkDays, _.toString)
        .map(_.text) shouldBe Seq("2026-10-20", "2026-10-30", "5 days left")
    }

    "say overdue after the due date" in { _ =>
      TeamDailyTaskBadges
        .dates(task(None, Seq.empty), dueDate.plusDays(1), WorkDayCalendar.DefaultWorkDays, _.toString)
        .map(_.text)
        .last shouldBe "Overdue"
    }
  }
}
