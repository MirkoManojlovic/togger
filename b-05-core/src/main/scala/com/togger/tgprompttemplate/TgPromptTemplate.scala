package com.togger.tgprompttemplate

import java.time.Instant
import java.util.UUID

case class TgPromptTemplate(id: UUID, createdBy: UUID, createdAt: Instant)

case class TgPromptTemplateVersion(
  version: UUID,
  tgPromptTemplateId: UUID,
  title: String,
  prompt: String,
  createdAt: Instant
) {

  def hasSameContentAs(other: TgPromptTemplateVersion): Boolean =
    title == other.title && prompt == other.prompt

}

/** A prompt with its latest version. */
case class TgPromptTemplateFull(tgPromptTemplate: TgPromptTemplate, latestVersion: TgPromptTemplateVersion)
