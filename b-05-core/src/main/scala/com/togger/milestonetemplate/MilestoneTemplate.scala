package com.togger.milestonetemplate

import java.time.Instant
import java.util.UUID

case class MilestoneTemplate(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant)

case class MilestoneTemplateVersion(
  version: UUID,
  milestoneTemplateId: UUID,
  name: String,
  description: String,
  createdAt: Instant
)

/** A step of a template: the step template it points to, with that step template's current content. */
case class MilestoneTemplateStep(stepTemplateId: UUID, name: String, description: String)

/** A template version with its steps in order. */
case class MilestoneTemplateVersionWithSteps(version: MilestoneTemplateVersion, steps: List[MilestoneTemplateStep]) {

  def hasSameContentAs(name: String, description: String, stepTemplateIds: List[UUID]): Boolean =
    version.name == name && version.description == description && steps.map(_.stepTemplateId) == stepTemplateIds

}

/** A template with its latest version and the steps of it. */
case class MilestoneTemplateFull(template: MilestoneTemplate, latest: MilestoneTemplateVersionWithSteps)
