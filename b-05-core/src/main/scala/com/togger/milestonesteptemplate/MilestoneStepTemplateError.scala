package com.togger.milestonesteptemplate

import com.togger.common.TgError

object MilestoneStepTemplateError {
  sealed trait MilestoneStepTemplateError extends TgError

  case object MilestoneStepTemplateNotFound extends MilestoneStepTemplateError {
    override def message: String = "Milestone step template not found"
  }

  case class StepTemplateInUse(milestoneTemplateNames: List[String]) extends MilestoneStepTemplateError {
    override def message: String =
      s"The milestone step template is used by the milestone templates: ${milestoneTemplateNames.mkString(", ")}"
  }
}
