package com.togger.milestonetemplate

import com.togger.common.TgError

import java.util.UUID

object MilestoneTemplateError {
  sealed trait MilestoneTemplateError extends TgError

  case object MilestoneTemplateNotFound extends MilestoneTemplateError {
    override def message: String = "Milestone template not found"
  }

  case class StepTemplateNotInProject(stepTemplateId: UUID) extends MilestoneTemplateError {
    override def message: String = s"Milestone step template $stepTemplateId does not belong to the project"
  }
}
