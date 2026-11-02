package com.togger.ui.teamdaily

import com.togger.ui.task.DateEmoji
import com.togger.common.WorkDayCalendar
import com.togger.dto.FindMilestoneResp
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.{BadgeSpec, BulkEditKind}

import java.time.DayOfWeek
import java.time.LocalDate

/** The badges of a task in the list of tasks to choose from: the labels go after the name, the milestone and priority
  * in a row below it, and the dates in a third. A task without a milestone or labels has no badge for them.
  */
object TeamDailyTaskBadges {

  def labels(task: TeamDailyTaskPlan): Seq[BadgeSpec] =
    task.labels.sortBy(_.name).map(label => BadgeSpec(label.badgeText, label.badgeColor))

  def milestoneAndPriority(task: TeamDailyTaskPlan, milestones: Seq[FindMilestoneResp]): Seq[BadgeSpec] =
    task.milestoneId
      .flatMap(id => milestones.find(_.id == id))
      .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
      .toSeq :+ BadgeSpec(task.priority.shortCode, task.priority.badgeColor)

  /** The start and due date, the length in work days, and the work days left until the due date (or overdue). */
  def dates(
    task: TeamDailyTaskPlan,
    today: LocalDate,
    workDays: Set[DayOfWeek],
    format: LocalDate => String
  ): Seq[BadgeSpec] =
    Seq(
      BadgeSpec(s"${DateEmoji.Start} ${format(task.startDate)}", TgBadgeColor.Gray),
      BadgeSpec(s"${DateEmoji.Due} ${format(task.dueDate)}", TgBadgeColor.Gray),
      duration(task.startDate, task.dueDate, workDays),
      daysLeft(task.dueDate, today, workDays)
    )

  /** The length of a task in work days, from its start to its due date. */
  def duration(
    startDate: LocalDate,
    dueDate: LocalDate,
    workDays: Set[DayOfWeek],
    kind: Option[BulkEditKind] = None
  ): BadgeSpec = {
    val days = WorkDayCalendar.countWorkDays(startDate, dueDate, workDays)
    BadgeSpec(s"${DateEmoji.Duration} ${if (days == 1) "1 day" else s"$days days"}", TgBadgeColor.Gray, kind)
  }

  def daysLeft(dueDate: LocalDate, today: LocalDate, workDays: Set[DayOfWeek]): BadgeSpec =
    if (dueDate.isBefore(today)) BadgeSpec(s"${DateEmoji.DaysLeft} Overdue", TgBadgeColor.Red)
    else BadgeSpec(s"${DateEmoji.DaysLeft} ${WorkDayCalendar.countWorkDays(today, dueDate, workDays)} days left", TgBadgeColor.Gray)
}
