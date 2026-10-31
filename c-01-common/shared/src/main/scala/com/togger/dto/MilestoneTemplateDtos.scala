package com.togger.dto

import java.time.Instant
import java.util.UUID

/** `stepTemplateIds` is the ordered list of steps a milestone created from the template starts with. */
case class CreateMilestoneTemplateReq(
  projectId: UUID,
  name: String,
  description: String,
  stepTemplateIds: List[UUID]
)

case class UpdateMilestoneTemplateReq(id: UUID, name: String, description: String, stepTemplateIds: List[UUID])

/** A step of a milestone template with the current name and description of its step template. */
case class MilestoneTemplateStepDto(stepTemplateId: UUID, name: String, description: String)

case class FindMilestoneTemplateResp(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  steps: List[MilestoneTemplateStepDto],
  latestVersion: UUID,
  updatedAt: Instant
)

case class MilestoneTemplateVersionDto(
  version: UUID,
  milestoneTemplateId: UUID,
  name: String,
  description: String,
  steps: List[MilestoneTemplateStepDto],
  createdAt: Instant
)
