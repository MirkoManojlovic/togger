package com.togger.controllers.tgaiaction

import com.togger.dto.{FindTgAiActionDefaultResp, FindTgAiActionResp}
import com.togger.tgaiaction.{TgAiActionDefault, TgAiActionWithBindings}

object TgAiActionDtoMapper {

  def toDto(withBindings: TgAiActionWithBindings): FindTgAiActionResp = {
    import withBindings.action
    FindTgAiActionResp(
      id = action.id,
      name = action.name,
      prompt = action.prompt,
      tgPromptTemplateId = action.tgPromptTemplateId,
      model = action.model,
      endpointIds = withBindings.endpointIds,
      createdAt = action.createdAt,
      updatedAt = action.updatedAt
    )
  }

  def toDefaultDto(default: TgAiActionDefault): FindTgAiActionDefaultResp =
    FindTgAiActionDefaultResp(default.projectId, default.aiEndpointId, default.tgAiActionId)
}
