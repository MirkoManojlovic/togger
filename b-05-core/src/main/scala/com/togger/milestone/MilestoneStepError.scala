package com.togger.milestone

import com.togger.common.TgError

import java.util.UUID

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

  case object LastStepOfMilestone extends MilestoneStepError {
    override def message: String = "The last step of a milestone cannot be deleted"
  }

  case object ShelfStepCannotBeDeleted extends MilestoneStepError {
    override def message: String = "The Shelf step of a milestone cannot be deleted"
  }

  case class StepHasTasks(count: Int) extends MilestoneStepError {
    override def message: String =
      s"The step has $count ${if (count == 1) "task" else "tasks"}; move or delete them before deleting the step"
  }

  case class StepNotInProject(stepId: UUID) extends MilestoneStepError {
    override def message: String = s"Milestone step $stepId does not belong to the project of the task"
  }

  case object StepsDoNotMatchMilestone extends MilestoneStepError {
    override def message: String = "The ordered steps must be exactly the steps of the milestone"
  }
}
