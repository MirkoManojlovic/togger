package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindTaskLabelResp
import com.togger.dto.FindTaskStatusResp
import com.togger.dto.SearchAccountDto
import com.togger.milestone.MilestoneStatus
import com.togger.task.DefaultTaskLabel
import com.togger.task.DefaultTaskStatus
import com.togger.ui.milestone.StepTestData
import com.togger.ui.components.TgBadgeColor
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BulkTaskBadgesTest extends AnyWordSpec with Matchers {

  private val projectId = UUID.randomUUID()
  private val monday = LocalDate.of(2026, 10, 12)

  private val backlog = FindTaskStatusResp(
    UUID.randomUUID(),
    projectId,
    DefaultTaskStatus.Backlog.statusName,
    "",
    DefaultTaskStatus.Backlog.emoji
  )
  private val bug =
    FindTaskLabelResp(UUID.randomUUID(), projectId, DefaultTaskLabel.Bug.labelName, "", DefaultTaskLabel.Bug.emoji)
  private val feature = FindTaskLabelResp(
    UUID.randomUUID(),
    projectId,
    DefaultTaskLabel.Feature.labelName,
    "",
    DefaultTaskLabel.Feature.emoji
  )
  private val member = SearchAccountDto(UUID.randomUUID(), "Ada", "Lovelace", "ada@example.com", UUID.randomUUID())
  private val milestone = FindMilestoneResp(
    UUID.randomUUID(),
    projectId,
    Instant.EPOCH,
    UUID.randomUUID(),
    "Release 1",
    "",
    MilestoneStatus.InProgress,
    monday,
    monday.plusDays(30),
    None,
    Instant.EPOCH
  )

  private val step = StepTestData.step(milestone.id, "Build")

  private val lookups =
    BulkLookups(Seq(backlog), Seq(bug, feature), Seq(member), Seq(milestone), WorkDayCalendar.DefaultWorkDays, Seq(step))

  private val defaults = BulkTaskDefaults.of(monday.minusDays(3), lookups.workDays, lookups.statuses)
  private val task = BulkTask.withDefaults(UUID.randomUUID(), "A", defaults)

  "BulkTaskBadges.of" should {
    "show the empty-state emoji for a milestone, labels and assignee that have no default" in {
      val rows = BulkTaskBadges.of(task, lookups, monday)
      (rows.primaryRow.head, rows.labelRow, rows.primaryRow(3)) shouldBe
        (
          BadgeSpec("🏁", TgBadgeColor.Gray, Some(BulkEditKind.Milestone)),
          Seq(BadgeSpec("🏷️", TgBadgeColor.Gray, Some(BulkEditKind.Labels))),
          BadgeSpec("👤", TgBadgeColor.Gray, Some(BulkEditKind.Assignee))
        )
    }

    "show priority as its short code and status as its emoji" in {
      val rows = BulkTaskBadges.of(task, lookups, monday)
      (rows.primaryRow(1).text, rows.primaryRow(2)) shouldBe ("p3", BadgeSpec(
        "📚",
        TgBadgeColor.Gray,
        Some(BulkEditKind.Status)
      ))
    }

    "show dates in full, the length in work days and the work days left" in {
      val rows = BulkTaskBadges.of(task, lookups, monday)
      rows.dateRow
        .map(_.text) shouldBe Seq("🗓️ Oct 12, 2026 | Mon", "🎯 Oct 16, 2026 | Fri", "⏱️ 5 days", "⏳ 5 days left")
    }

    "show the milestone and step name, label emoji and assignee name when set" in {
      val full = task.copy(
        milestoneStepId = Some(step.id),
        labelIds = Set(bug.id, feature.id),
        assigneeId = Some(member.id)
      )
      val rows = BulkTaskBadges.of(full, lookups, monday)
      (rows.primaryRow.head.text, rows.labelRow.map(_.text), rows.primaryRow(3).text) shouldBe
        ("Release 1 › Build", Seq("🐞", "🆕"), "Ada Lovelace")
    }

    "put the assignee after the status in the first of two rows, and the labels apart from them" in {
      val rows = BulkTaskBadges.of(task, lookups, monday)
      (rows.rows.map(_.size), rows.primaryRow.map(_.text)) shouldBe (Seq(4, 4), Seq("🏁", "p3", "📚", "👤"))
    }
  }

  "BulkTaskBadges kinds" should {
    "say which attribute each badge edits" in {
      val rows = BulkTaskBadges.of(task, lookups, monday)
      (rows.primaryRow.map(_.kind), rows.dateRow.map(_.kind), rows.labelRow.map(_.kind)) shouldBe
        (
          Seq(
            Some(BulkEditKind.Milestone),
            Some(BulkEditKind.Priority),
            Some(BulkEditKind.Status),
            Some(BulkEditKind.Assignee)
          ),
          Seq(Some(BulkEditKind.StartDate), Some(BulkEditKind.DueDate), Some(BulkEditKind.WorkDays), None),
          Seq(Some(BulkEditKind.Labels))
        )
    }

    "group the label badges of a task into one thing to click and leave other badges alone" in {
      val labelled = task.copy(labelIds = Set(bug.id, feature.id))
      val rows = BulkTaskBadges.of(labelled, lookups, monday)
      (BulkTaskBadges.grouped(rows.labelRow).map(_.size), BulkTaskBadges.grouped(rows.primaryRow).map(_.size)) shouldBe
        (Seq(2), Seq(1, 1, 1, 1))
    }

    "not group badges that have no attribute to edit" in {
      val plain = Seq(BadgeSpec("a", TgBadgeColor.Gray), BadgeSpec("b", TgBadgeColor.Gray))
      BulkTaskBadges.grouped(plain).map(_.size) shouldBe Seq(1, 1)
    }
  }

  "EmojiPrefix.of" should {
    "take the emoji before the name" in {
      (EmojiPrefix.of("▶️ In progress"), EmojiPrefix.of("🕰️ Waiting")) shouldBe ("▶️", "🕰️")
    }

    "fall back to the first character for a name without an emoji" in {
      EmojiPrefix.of("bug") shouldBe "b"
    }

    "fall back to a dot for an empty name" in {
      EmojiPrefix.of("") shouldBe EmojiPrefix.Fallback
    }
  }
}
