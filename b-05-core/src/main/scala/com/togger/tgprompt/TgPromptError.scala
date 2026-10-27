package com.togger.tgprompt

import com.togger.common.TgError

object TgPromptError {
  sealed trait TgPromptError extends TgError

  case object TgPromptNotFound extends TgPromptError {
    override def message: String = "Prompt not found"
  }

  case object VersionNotFound extends TgPromptError {
    override def message: String = "Prompt version not found"
  }

  case object CannotDeleteLastVersion extends TgPromptError {
    override def message: String = "Cannot delete the only remaining version of a prompt, delete the prompt instead"
  }
}
