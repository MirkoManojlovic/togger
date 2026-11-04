package com.togger.ui.milestone

import com.togger.dto.FindMilestoneStepResp
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.task.bulk.BadgeSpec
import com.togger.ui.task.bulk.BulkEditKind

import java.util.UUID

/** The badge naming the step a task is in. */
object MilestoneStepBadges {

  /** Nothing when the task has no step or the step is not among the loaded ones. */
  def of(stepId: Option[UUID], steps: Seq[FindMilestoneStepResp], editable: Boolean = false): Option[BadgeSpec] =
    stepId
      .flatMap(id => steps.find(_.id == id))
      .map(step => BadgeSpec(step.name, TgBadgeColor.Alternative, Option.when(editable)(BulkEditKind.MilestoneStep)))
}
