package com.togger.ui.task.bulk

import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp, FindTaskStatusResp, SearchAccountDto}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.TgDateFormater

import java.time.DayOfWeek

case class BadgeSpec(text: String, color: TgBadgeColor)

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
  workDays: Set[DayOfWeek]
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
  val EmptyLabels = "🏷️"
  val EmptyAssignee = "👤"

  private val dateFormater = new TgDateFormater

  def of(task: BulkTask, lookups: BulkLookups): BulkTaskBadgeRows =
    BulkTaskBadgeRows(
      labelRow = labels(task, lookups),
      primaryRow = Seq(milestone(task, lookups), priority(task), status(task, lookups), assignee(task, lookups)),
      dateRow = Seq(
        BadgeSpec(dateFormater.asAmericanDateWithDayAbrv(task.startDate), TgBadgeColor.Gray),
        BadgeSpec(dateFormater.asAmericanDateWithDayAbrv(task.dueDate), TgBadgeColor.Gray),
        BadgeSpec(
          WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, lookups.workDays).toString,
          TgBadgeColor.Gray
        )
      )
    )

  private def milestone(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.milestoneId
      .flatMap(id => lookups.milestones.find(_.id == id))
      .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
      .getOrElse(BadgeSpec(EmptyMilestone, TgBadgeColor.Gray))

  private def priority(task: BulkTask): BadgeSpec =
    BadgeSpec(task.priority.shortCode, task.priority.badgeColor)

  private def status(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.statusId
      .flatMap(id => lookups.statuses.find(_.id == id))
      .map(status => BadgeSpec(EmojiPrefix.of(status.name), status.badgeColor))
      .getOrElse(BadgeSpec(EmojiPrefix.Fallback, TgBadgeColor.Gray))

  private def labels(task: BulkTask, lookups: BulkLookups): Seq[BadgeSpec] = {
    val specs = lookups.labels
      .filter(label => task.labelIds.contains(label.id))
      .sortBy(_.name)
      .map(label => BadgeSpec(EmojiPrefix.of(label.name), label.badgeColor))
    if (specs.isEmpty) Seq(BadgeSpec(EmptyLabels, TgBadgeColor.Gray)) else specs
  }

  private def assignee(task: BulkTask, lookups: BulkLookups): BadgeSpec =
    task.assigneeId
      .flatMap(id => lookups.members.find(_.id == id))
      .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray))
      .getOrElse(BadgeSpec(EmptyAssignee, TgBadgeColor.Gray))
}
