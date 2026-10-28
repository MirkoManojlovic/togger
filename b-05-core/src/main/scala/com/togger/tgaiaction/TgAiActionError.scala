package com.togger.tgaiaction

import com.togger.common.TgError

object TgAiActionError {
  sealed trait TgAiActionError extends TgError

  case object TgAiActionNotFound extends TgAiActionError {
    override def message: String = "AI action not found"
  }

  case object DuplicateName extends TgAiActionError {
    override def message: String = "An AI action with this name already exists"
  }

  case object UnknownEndpoint extends TgAiActionError {
    override def message: String = "Unknown AI endpoint"
  }

  case object UnknownPromptTemplate extends TgAiActionError {
    override def message: String = "Prompt template not found"
  }

  case object ActionNotBoundToEndpoint extends TgAiActionError {
    override def message: String = "The AI action is not bound to this AI endpoint"
  }

  case object NotAProjectMember extends TgAiActionError {
    override def message: String = "You are not a member of this project"
  }
}
