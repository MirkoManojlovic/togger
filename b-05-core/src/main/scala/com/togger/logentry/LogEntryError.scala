package com.togger.logentry

import com.togger.common.TgError

object LogEntryError {
  sealed trait LogEntryError extends TgError

  case object LogEntryNotFound extends LogEntryError {
    override def message: String = "Log entry not found"
  }

  case object VersionNotFound extends LogEntryError {
    override def message: String = "Log entry version not found"
  }

  case object CannotEditLatestVersion extends LogEntryError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends LogEntryError {
    override def message: String = "Cannot delete the only remaining version of a log entry"
  }
}


