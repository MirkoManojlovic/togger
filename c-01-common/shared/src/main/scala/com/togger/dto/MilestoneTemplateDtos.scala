package com.togger.dto

import java.time.Instant
import java.util.UUID

/** `stages` are the steps a milestone created from the template starts with: the outer list is the sequence of stages,
  * the step templates of an inner list run in parallel.
  */
case class CreateMilestoneTemplateReq(
  projectId: UUID,
  name: String,
  description: String,
  stages: List[List[UUID]]
)

case class UpdateMilestoneTemplateReq(id: UUID, name: String, description: String, stages: List[List[UUID]])

/** A step of a milestone template with the current name and description of its step template. The steps are ordered by
  * `stage`; steps with the same stage run in parallel.
  */
case class MilestoneTemplateStepDto(stepTemplateId: UUID, name: String, description: String, stage: Int)

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
