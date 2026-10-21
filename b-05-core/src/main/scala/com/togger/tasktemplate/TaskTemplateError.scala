package com.togger.tasktemplate

import com.togger.common.TgError

object TaskTemplateError {
  sealed trait TaskTemplateError extends TgError

  case object TaskTemplateNotFound extends TaskTemplateError {
    override def message: String = "Task template not found"
  }

  case object LabelAlreadyHasTemplate extends TaskTemplateError {
    override def message: String = "The task label is already linked to another template"
  }

  case object LabelNotInProject extends TaskTemplateError {
    override def message: String = "The task label does not belong to the project of the template"
  }
}
