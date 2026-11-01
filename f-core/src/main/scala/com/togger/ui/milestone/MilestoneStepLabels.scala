package com.togger.ui.milestone

import com.togger.dto.{FindMilestoneResp, FindMilestoneStepResp}

import java.util.UUID

/** How a step is named where a task shows or picks it: "Milestone › Step". */
object MilestoneStepLabels {

  private val Separator = " › "

  def label(step: FindMilestoneStepResp, milestones: Seq[FindMilestoneResp]): String =
    milestones.find(_.id == step.milestoneId).fold(step.name)(milestone => s"${milestone.name}$Separator${step.name}")

  /** The label of the step with the id; nothing when the step is not among the loaded ones. */
  def labelOf(stepId: UUID, steps: Seq[FindMilestoneStepResp], milestones: Seq[FindMilestoneResp]): Option[String] =
    steps.find(_.id == stepId).map(label(_, milestones))

  /** The steps of the given milestones, by milestone (due date, then name) and then position; steps of a milestone that
    * is not among the given ones are left out.
    */
  def ordered(steps: Seq[FindMilestoneStepResp], milestones: Seq[FindMilestoneResp]): Seq[FindMilestoneStepResp] = {
    val milestoneOrder = milestones.sortBy(milestone => (milestone.dueDate.toEpochDay, milestone.name)).map(_.id).zipWithIndex.toMap
    steps.filter(step => milestoneOrder.contains(step.milestoneId)).sortBy(step => (milestoneOrder(step.milestoneId), step.position))
  }
}
