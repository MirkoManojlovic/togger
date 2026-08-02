package com.togger.task

import com.togger.common.TgError

object LinkError {
  sealed trait LinkError extends TgError

  case object LinkNotFound extends LinkError {
    override def message: String = "Link not found"
  }

  case object VersionNotFound extends LinkError {
    override def message: String = "Link version not found"
  }

  case object CannotEditLatestVersion extends LinkError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends LinkError {
    override def message: String = "Cannot delete the only remaining version of a link"
  }
}
