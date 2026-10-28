package com.togger.tgaiaction

import java.time.Instant
import java.util.UUID

case class TgAiAction(
  id: UUID,
  createdBy: UUID,
  name: String,
  prompt: String,
  tgPromptTemplateId: Option[UUID],
  model: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class TgAiActionEndpoint(tgAiActionId: UUID, aiEndpointId: Int)

case class TgAiActionDefault(accountId: UUID, projectId: UUID, aiEndpointId: Int, tgAiActionId: UUID)

case class TgAiActionWithBindings(action: TgAiAction, endpointIds: List[Int])
