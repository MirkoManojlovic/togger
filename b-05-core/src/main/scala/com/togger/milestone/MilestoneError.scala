package com.togger.milestone

import com.togger.common.TgError

import java.time.LocalDate

object MilestoneError {
  sealed trait MilestoneError extends TgError

  case object MilestoneNotFound extends MilestoneError {
    override def message: String = "Milestone not found"
  }

  case class StepsOutsideNewRange(stepNames: List[String], startDate: LocalDate, dueDate: LocalDate)
      extends MilestoneError {
    override def message: String =
      s"These steps would lie outside the milestone range $startDate - $dueDate: ${stepNames.mkString(", ")}"
  }

  case class MilestoneHasTasks(count: Int) extends MilestoneError {
    override def message: String =
      s"The milestone has $count ${if (count == 1) "task" else "tasks"}; move or delete them before deleting the milestone"
  }

  case class InvalidStep(stepName: String, reason: String) extends MilestoneError {
    override def message: String = s"Step '$stepName': $reason"
  }

  case object VersionNotFound extends MilestoneError {
    override def message: String = "Milestone version not found"
  }

  case object CannotEditLatestVersion extends MilestoneError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends MilestoneError {
    override def message: String = "Cannot delete the only remaining version of a milestone"
  }
}
