package com.togger.ui.teamdaily

import com.togger.common.WorkDayCalendar
import com.togger.dto.FindMilestoneResp
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.BadgeSpec

import java.time.{DayOfWeek, LocalDate}

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

  /** The start and due date, and the work days left until the due date (or overdue). */
  def dates(task: TeamDailyTaskPlan, today: LocalDate, workDays: Set[DayOfWeek], format: LocalDate => String): Seq[BadgeSpec] =
    Seq(
      BadgeSpec(format(task.startDate), TgBadgeColor.Gray),
      BadgeSpec(format(task.dueDate), TgBadgeColor.Gray),
      daysLeft(task.dueDate, today, workDays)
    )

  def daysLeft(dueDate: LocalDate, today: LocalDate, workDays: Set[DayOfWeek]): BadgeSpec =
    if (dueDate.isBefore(today)) BadgeSpec("Overdue", TgBadgeColor.Red)
    else BadgeSpec(s"${WorkDayCalendar.countWorkDays(today, dueDate, workDays)} days left", TgBadgeColor.Gray)
}
