package com.togger.dto

import java.time.Instant
import java.util.UUID

case class CreateTgAiActionReq(
  name: String,
  prompt: String,
  tgPromptTemplateId: Option[UUID],
  model: String,
  endpointIds: List[Int]
)

case class UpdateTgAiActionReq(
  id: UUID,
  name: String,
  prompt: String,
  tgPromptTemplateId: Option[UUID],
  model: String,
  endpointIds: List[Int]
)

case class FindTgAiActionResp(
  id: UUID,
  name: String,
  prompt: String,
  tgPromptTemplateId: Option[UUID],
  model: String,
  endpointIds: List[Int],
  createdAt: Instant,
  updatedAt: Instant
)

case class TgAiEndpointDto(id: Int, name: String)

case class SetTgAiActionDefaultReq(projectId: UUID, endpointId: Int, actionId: UUID)

case class FindTgAiActionDefaultResp(projectId: UUID, endpointId: Int, actionId: UUID)
