package com.togger.milestone

import com.togger.common.TgError

object MilestoneDateError {
  sealed trait MilestoneDateError extends TgError

  case object MilestoneDateNotFound extends MilestoneDateError {
    override def message: String = "Milestone date not found"
  }

  case object VersionNotFound extends MilestoneDateError {
    override def message: String = "Milestone date version not found"
  }

  case object CannotEditLatestVersion extends MilestoneDateError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends MilestoneDateError {
    override def message: String = "Cannot delete the only remaining version of a milestone date"
  }
}
