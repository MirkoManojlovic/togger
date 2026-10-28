package com.togger.tgprompttemplate

import com.togger.common.TgError

object TgPromptTemplateError {
  sealed trait TgPromptTemplateError extends TgError

  case object TgPromptTemplateNotFound extends TgPromptTemplateError {
    override def message: String = "Prompt not found"
  }

  case object VersionNotFound extends TgPromptTemplateError {
    override def message: String = "Prompt version not found"
  }

  case object CannotDeleteLastVersion extends TgPromptTemplateError {
    override def message: String = "Cannot delete the only remaining version of a prompt, delete the prompt instead"
  }
}
