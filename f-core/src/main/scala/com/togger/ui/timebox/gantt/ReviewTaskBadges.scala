package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.FindTaskRespFull
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.milestone.MilestoneStepBadges
import com.togger.ui.task.bulk.BadgeSpec
import com.togger.ui.task.bulk.BulkTaskBadges
import com.togger.ui.task.bulk.BulkTaskCard

/** The badges of a task as the create timebox page shows them next to its Gantt bar, but not to click on. */
object ReviewTaskBadges {

  def labels(task: FindTaskRespFull): HtmlElement = BulkTaskCard.readOnlyRow(labelSpecs(task))

  def primary(
    task: FindTaskRespFull,
    milestones: Seq[FindMilestoneResp],
    steps: Seq[FindMilestoneStepResp]
  ): HtmlElement =
    BulkTaskCard.readOnlyRow(primarySpecs(task, milestones, steps))

  private def labelSpecs(task: FindTaskRespFull): Seq[BadgeSpec] =
    if (task.taskLabels.isEmpty) Seq(BadgeSpec(BulkTaskBadges.EmptyLabels, TgBadgeColor.Gray))
    else task.taskLabels.sortBy(_.name).map(label => BadgeSpec(label.badgeText, label.badgeColor))

  private def primarySpecs(
    task: FindTaskRespFull,
    milestones: Seq[FindMilestoneResp],
    steps: Seq[FindMilestoneStepResp]
  ): Seq[BadgeSpec] =
    Seq(
      task.milestone
        .flatMap(id => milestones.find(_.id == id))
        .map(milestone => BadgeSpec(milestone.name, TgBadgeColor.Alternative))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray))
    ) ++ MilestoneStepBadges.of(task.milestoneStepId, steps) ++ Seq(
      BadgeSpec(task.priority.shortCode, task.priority.badgeColor),
      BadgeSpec(task.taskStatus.badgeText, task.taskStatus.badgeColor),
      task.assignedTo
        .map(member => BadgeSpec(member.fullName, TgBadgeColor.Gray))
        .getOrElse(BadgeSpec(BulkTaskBadges.EmptyAssignee, TgBadgeColor.Gray))
    )
}
