package com.togger.milestone

import com.togger.common.TgError

object MilestoneSummaryError {
  sealed trait MilestoneSummaryError extends TgError

  case object MilestoneSummaryNotFound extends MilestoneSummaryError {
    override def message: String = "Milestone summary not found"
  }

  case object VersionNotFound extends MilestoneSummaryError {
    override def message: String = "Milestone summary version not found"
  }

  case object CannotEditLatestVersion extends MilestoneSummaryError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends MilestoneSummaryError {
    override def message: String = "Cannot delete the only remaining version of a milestone summary"
  }
}
