package com.togger.timebox

import com.togger.common.TgError

import java.util.UUID

object TimeboxError {
  sealed trait TimeboxError extends TgError

  case object TimeboxNotFound extends TimeboxError {
    override def message: String = "Timebox not found"
  }

  case object ChangeCommentEmpty extends TimeboxError {
    override def message: String = "Change comment must not be empty"
  }

  case class TaskNotFound(taskId: UUID) extends TimeboxError {
    override def message: String = s"Task $taskId not found"
  }

  case class TaskNotInProject(taskId: UUID) extends TimeboxError {
    override def message: String = s"Task $taskId does not belong to the project of the timebox"
  }
}
