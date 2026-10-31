package com.togger.milestonesteptemplate

import java.time.Instant
import java.util.UUID

case class MilestoneStepTemplate(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant)

case class MilestoneStepTemplateVersion(
  version: UUID,
  milestoneStepTemplateId: UUID,
  name: String,
  description: String,
  createdAt: Instant
) {

  def hasSameContentAs(other: MilestoneStepTemplateVersion): Boolean =
    name == other.name && description == other.description

}

/** A template with its latest version. */
case class MilestoneStepTemplateFull(template: MilestoneStepTemplate, latestVersion: MilestoneStepTemplateVersion)
