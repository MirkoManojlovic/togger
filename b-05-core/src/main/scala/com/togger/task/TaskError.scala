package com.togger.task

import com.togger.common.TgError

object TaskError {
  sealed trait TaskError extends TgError

  case object TaskNotFound extends TaskError {
    override def message: String = "Task not found"
  }

  case object VersionNotFound extends TaskError {
    override def message: String = "Task version not found"
  }

  case object CannotEditLatestVersion extends TaskError {
    override def message: String = "Cannot edit the latest version directly, save a new version instead"
  }

  case object CannotDeleteLastVersion extends TaskError {
    override def message: String = "Cannot delete the only remaining version of a task"
  }
}
