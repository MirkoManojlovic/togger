package com.togger.dto

import java.time.Instant
import java.util.UUID

case class CreateTgPromptReq(title: String, prompt: String)

/** Saves a new version of an existing prompt, versions are never edited. */
case class NewTgPromptVersionReq(id: UUID, title: String, prompt: String)

case class FindTgPromptResp(
  id: UUID,
  title: String,
  prompt: String,
  latestVersion: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

case class TgPromptVersionDto(version: UUID, tgPromptId: UUID, title: String, prompt: String, createdAt: Instant)
