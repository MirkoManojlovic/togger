package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull}
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.{BadgeSpec, BulkTaskBadges, BulkTaskCard}

/** The badges of a task as the create timebox page shows them next to its Gantt bar, but not to click on. */
object ReviewTaskBadges {

  def labels(task: FindTaskRespFull): HtmlElement = BulkTaskCard.readOnlyRow(labelSpecs(task))

  def primary(task: FindTaskRespFull, milestones: Seq[FindMilestoneResp]): HtmlElement =
    BulkTaskCard.readOnlyRow(primarySpecs(task, milestones))

  private def labelSpecs(task: FindTaskRespFull): Seq[BadgeSpec] =
    if (task.taskLabels.isEmpty) Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray))
    else task.taskLabels.sortBy(_.name).map(label => BadgeSpec(label.name, label.badgeColor))

  private def primarySpecs(task: FindTaskRespFull, milestones: Seq[FindMilestoneResp]): Seq[BadgeSpec] =
    Seq(
      task.milestone
        .flatMap(id => milestones.find(_.id == id))
        .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray)),
      BadgeSpec(task.priority.shortCode, task.priority.badgeColor),
      BadgeSpec(task.taskStatus.name, task.taskStatus.badgeColor),
      task.assignedTo
        .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray))
    )
}
