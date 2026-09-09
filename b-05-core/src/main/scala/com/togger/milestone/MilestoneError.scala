package com.togger.milestone

import com.togger.common.TgError

object MilestoneError {
  sealed trait MilestoneError extends TgError

  case object MilestoneNotFound extends MilestoneError {
    override def message: String = "Milestone not found"
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
