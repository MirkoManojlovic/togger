package com.togger.dto

import java.time.Instant
import java.util.UUID

case class CreateMilestoneStepTemplateReq(projectId: UUID, name: String, description: String)

case class UpdateMilestoneStepTemplateReq(id: UUID, name: String, description: String)

case class FindMilestoneStepTemplateResp(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  latestVersion: UUID,
  updatedAt: Instant
)

case class MilestoneStepTemplateVersionDto(
  version: UUID,
  milestoneStepTemplateId: UUID,
  name: String,
  description: String,
  createdAt: Instant
)
