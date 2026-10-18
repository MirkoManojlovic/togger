package com.togger.ui.teamdaily

import com.togger.dto.FindMilestoneResp
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.BadgeSpec

/** The badges of a task in the list of tasks to choose from: the labels go after the name, the milestone and priority
  * in a row below it. A task without a milestone or labels has no badge for them.
  */
object TeamDailyTaskBadges {

  def labels(task: TeamDailyTaskPlan): Seq[BadgeSpec] =
    task.labels.sortBy(_.name).map(label => BadgeSpec(label.name, label.badgeColor))

  def milestoneAndPriority(task: TeamDailyTaskPlan, milestones: Seq[FindMilestoneResp]): Seq[BadgeSpec] =
    task.milestoneId
      .flatMap(id => milestones.find(_.id == id))
      .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
      .toSeq :+ BadgeSpec(task.priority.shortCode, task.priority.badgeColor)
}
