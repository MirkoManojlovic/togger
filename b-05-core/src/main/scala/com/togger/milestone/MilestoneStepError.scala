package com.togger.milestone

import com.togger.common.TgError

object MilestoneStepError {
  sealed trait MilestoneStepError extends TgError

  case object MilestoneStepNotFound extends MilestoneStepError {
    override def message: String = "Milestone step not found"
  }

  case object ChangeCommentEmpty extends MilestoneStepError {
    override def message: String = "Change comment must not be empty"
  }

  case object EndDateRequiredWhenClosed extends MilestoneStepError {
    override def message: String = "End date is required when the status is Done or Abandoned"
  }

  case object StepsDoNotMatchMilestone extends MilestoneStepError {
    override def message: String = "The ordered steps must be exactly the steps of the milestone"
  }
}
