package com.togger.ui.task.bulk

import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.FindTaskLabelResp
import com.togger.dto.FindTaskStatusResp
import com.togger.dto.SearchAccountDto
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.milestone.MilestoneStepLabels
import com.togger.ui.task.TgDateFormater
import com.togger.ui.teamdaily.TeamDailyTaskBadges

import java.time.DayOfWeek
import java.time.LocalDate

/** @param kind
  *   the attribute this badge shows, so clicking it can edit it; none for a badge that is only a label
  */
case class BadgeSpec(text: String, color: TgBadgeColor, kind: Option[BulkEditKind] = None)

/** A task's badges: the labels go right after the task name, the rest in two rows below it. */
case class BulkTaskBadgeRows(
  labelRow: Seq[BadgeSpec],
  primaryRow: Seq[BadgeSpec],
  dateRow: Seq[BadgeSpec]
) {
  def rows: Seq[Seq[BadgeSpec]] = Seq(primaryRow, dateRow)
}

/** What a task's ids resolve against: the loaded project data and the project's work days. */
case class BulkLookups(
  statuses: Seq[FindTaskStatusResp],
  labels: Seq[FindTaskLabelResp],
  members: Seq[SearchAccountDto],
  milestones: Seq[FindMilestoneResp],
  workDays: Set[DayOfWeek],
  steps: Seq[FindMilestoneStepResp] = Seq.empty
)

object EmojiPrefix {

  val Fallback = "•"

  /** Statuses and labels keep their emoji as a prefix of the name ("📚 Backlog"), so this is what comes before the
    * first letter or digit; a name without an emoji gets its first character instead.
    */
  def of(name: String): String = {
    val emoji = name.takeWhile(c => !c.isLetterOrDigit).trim
    if (emoji.nonEmpty) emoji else Some(name.trim.take(1)).filter(_.nonEmpty).getOrElse(Fallback)
  }
}

object BulkTaskBadges {

  val EmptyMilestone = "🏁"
  val EmptyMilestoneStep = "🪜"
  val EmptyLabels = "🏷️"
  val EmptyAssignee = "👤"

  private val dateFormater = new TgDateFormater

  def of(task: BulkTask, lookups: BulkLookups, today: LocalDate): BulkTaskBadgeRows =
    BulkTaskBadgeRows(
      labelRow = labels(task, lookups),
      primaryRow = Seq(
        milestone(task, lookups),
        milestoneStep(task, lookups),
        priority(task),
        status(task, lookups),
        assignee(task, lookups)
      ),
      dateRow = Seq(
        BadgeSpec(
          dateFormater.asStartDate(task.startDate),
          TgBadgeColor.Gray,
          Some(BulkEditKind.StartDate)
        ),
        BadgeSpec(
          dateFormater.asDueDate(task.dueDate),
          TgBadgeColor.Gray,
          Some(BulkEditKind.DueDate)
        ),
        TeamDailyTaskBadges.duration(task.startDate, task.dueDate, lookups.workDays, Some(BulkEditKind.WorkDays)),
        TeamDailyTaskBadges.daysLeft(task.dueDate, today, lookups.workDays)
      )
    )

  private def milestone(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.milestoneStepId
      .flatMap(id => MilestoneStepLabels.milestoneOf(id, lookups.steps, lookups.milestones))
      .map(found => BadgeSpec(found.name, TgBadgeColor.Alternative, Some(BulkEditKind.Milestone)))
      .getOrElse(BadgeSpec(EmptyMilestone, TgBadgeColor.Gray, Some(BulkEditKind.Milestone)))

  private def milestoneStep(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.milestoneStepId
      .flatMap(id => lookups.steps.find(_.id == id))
      .map(found => BadgeSpec(found.name, TgBadgeColor.Alternative, Some(BulkEditKind.MilestoneStep)))
      .getOrElse(BadgeSpec(EmptyMilestoneStep, TgBadgeColor.Gray, Some(BulkEditKind.MilestoneStep)))

  private def priority(task: BulkTask): BadgeSpec =
    BadgeSpec(task.priority.shortCode, task.priority.badgeColor, Some(BulkEditKind.Priority))

  private def status(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.statusId
      .flatMap(id => lookups.statuses.find(_.id == id))
      .map(status => BadgeSpec(status.emoji, status.badgeColor, Some(BulkEditKind.Status)))
      .getOrElse(BadgeSpec(EmojiPrefix.Fallback, TgBadgeColor.Gray, Some(BulkEditKind.Status)))

  private def labels(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] = {
    val specs = lookups.labels
      .filter(label => task.labelIds.contains(label.id))
      .sortBy(_.name)
      .map(label => BadgeSpec(label.emoji, label.badgeColor, Some(BulkEditKind.Labels)))
    if (specs.isEmpty) Seq(BadgeSpec(EmptyLabels, TgBadgeColor.Gray, Some(BulkEditKind.Labels))) else specs
  }

  private def assignee(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.assigneeId
      .flatMap(id => lookups.members.find(_.id == id))
      .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))
      .getOrElse(BadgeSpec(EmptyAssignee, TgBadgeColor.Gray, Some(BulkEditKind.Assignee)))

  /** Neighbours that edit the same attribute go together, so all the label badges of a task are one thing to click. */
  def grouped(row: Seq[BadgeSpec]): Seq[Seq[BadgeSpec]] =
    row.foldLeft(Vector.empty[Vector[BadgeSpec]]) { (groups, spec) =>
      if (spec.kind.isDefined && groups.lastOption.exists(_.head.kind == spec.kind))
        groups.init :+ (groups.last :+ spec)
      else groups :+ Vector(spec)
    }
}
