package com.togger.controllers.tgprompttemplate

import com.togger.dto.{FindTgPromptTemplateResp, TgPromptTemplateVersionDto}
import com.togger.tgprompttemplate.{TgPromptTemplateFull, TgPromptTemplateVersion}

object TgPromptTemplateDtoMapper {

  def toDto(full: TgPromptTemplateFull): FindTgPromptTemplateResp =
    FindTgPromptTemplateResp(
      id = full.tgPromptTemplate.id,
      title = full.latestVersion.title,
      prompt = full.latestVersion.prompt,
      latestVersion = full.latestVersion.version,
      createdAt = full.tgPromptTemplate.createdAt,
      updatedAt = full.latestVersion.createdAt
    )

  def toVersionDto(version: TgPromptTemplateVersion): TgPromptTemplateVersionDto =
    TgPromptTemplateVersionDto(
      version = version.version,
      tgPromptTemplateId = version.tgPromptTemplateId,
      title = version.title,
      prompt = version.prompt,
      createdAt = version.createdAt
    )
}
