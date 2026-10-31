package com.togger.controllers.milestone

import com.togger.dto.{FindMilestoneStepTemplateResp, MilestoneStepTemplateVersionDto}
import com.togger.milestonesteptemplate.{MilestoneStepTemplateFull, MilestoneStepTemplateVersion}

object MilestoneStepTemplateDtoMapper {

  def toDto(full: MilestoneStepTemplateFull): FindMilestoneStepTemplateResp =
    FindMilestoneStepTemplateResp(
      id = full.template.id,
      projectId = full.template.projectId,
      name = full.latestVersion.name,
      description = full.latestVersion.description,
      latestVersion = full.latestVersion.version,
      updatedAt = full.latestVersion.createdAt
    )

  def toVersionDto(version: MilestoneStepTemplateVersion): MilestoneStepTemplateVersionDto =
    MilestoneStepTemplateVersionDto(
      version = version.version,
      milestoneStepTemplateId = version.milestoneStepTemplateId,
      name = version.name,
      description = version.description,
      createdAt = version.createdAt
    )
}
