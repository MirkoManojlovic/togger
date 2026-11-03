package com.togger.controllers.milestone

import com.togger.dto.FindMilestoneTemplateResp
import com.togger.dto.MilestoneTemplateStepDto
import com.togger.dto.MilestoneTemplateVersionDto
import com.togger.milestonetemplate.MilestoneTemplateFull
import com.togger.milestonetemplate.MilestoneTemplateStep
import com.togger.milestonetemplate.MilestoneTemplateVersionWithSteps

object MilestoneTemplateDtoMapper {

  def toDto(full: MilestoneTemplateFull): FindMilestoneTemplateResp =
    FindMilestoneTemplateResp(
      id = full.template.id,
      projectId = full.template.projectId,
      name = full.latest.version.name,
      description = full.latest.version.description,
      steps = full.latest.steps.map(toStepDto),
      latestVersion = full.latest.version.version,
      updatedAt = full.latest.version.createdAt
    )

  def toVersionDto(versionWithSteps: MilestoneTemplateVersionWithSteps): MilestoneTemplateVersionDto =
    MilestoneTemplateVersionDto(
      version = versionWithSteps.version.version,
      milestoneTemplateId = versionWithSteps.version.milestoneTemplateId,
      name = versionWithSteps.version.name,
      description = versionWithSteps.version.description,
      steps = versionWithSteps.steps.map(toStepDto),
      createdAt = versionWithSteps.version.createdAt
    )

  private def toStepDto(step: MilestoneTemplateStep): MilestoneTemplateStepDto =
    MilestoneTemplateStepDto(step.stepTemplateId, step.name, step.description)
}
