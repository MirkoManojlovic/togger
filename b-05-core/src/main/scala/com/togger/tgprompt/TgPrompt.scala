package com.togger.tgprompt

import java.time.Instant
import java.util.UUID

case class TgPrompt(id: UUID, createdBy: UUID, createdAt: Instant)

case class TgPromptVersion(
  version: UUID,
  tgPromptId: UUID,
  title: String,
  prompt: String,
  createdAt: Instant
) {

  def hasSameContentAs(other: TgPromptVersion): Boolean =
    title == other.title && prompt == other.prompt

}

/** A prompt with its latest version. */
case class TgPromptFull(tgPrompt: TgPrompt, latestVersion: TgPromptVersion)
