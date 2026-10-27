package com.togger.controllers.tgprompt

import com.togger.dto.{FindTgPromptResp, TgPromptVersionDto}
import com.togger.tgprompt.{TgPromptFull, TgPromptVersion}

object TgPromptDtoMapper {

  def toDto(full: TgPromptFull): FindTgPromptResp =
    FindTgPromptResp(
      id = full.tgPrompt.id,
      title = full.latestVersion.title,
      prompt = full.latestVersion.prompt,
      latestVersion = full.latestVersion.version,
      createdAt = full.tgPrompt.createdAt,
      updatedAt = full.latestVersion.createdAt
    )

  def toVersionDto(version: TgPromptVersion): TgPromptVersionDto =
    TgPromptVersionDto(
      version = version.version,
      tgPromptId = version.tgPromptId,
      title = version.title,
      prompt = version.prompt,
      createdAt = version.createdAt
    )
}
