package com.togger.dto

import java.time.Instant
import java.util.UUID

case class CreateTgPromptTemplateReq(title: String, prompt: String)

/** Saves a new version of an existing prompt, versions are never edited. */
case class NewTgPromptTemplateVersionReq(id: UUID, title: String, prompt: String)

case class FindTgPromptTemplateResp(
  id: UUID,
  title: String,
  prompt: String,
  latestVersion: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

case class TgPromptTemplateVersionDto(version: UUID, tgPromptTemplateId: UUID, title: String, prompt: String, createdAt: Instant)
