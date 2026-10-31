package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.milestone.DefaultMilestoneStepTemplates
import com.togger.milestonesteptemplate.{MilestoneStepTemplate, MilestoneStepTemplateVersion}

import java.util.UUID

class DefaultMilestoneStepTemplateService(tgClock: TgClock) {

  /** The default step templates with their first version, in the order of [[DefaultMilestoneStepTemplateService.defaults]]. */
  def createDefaultMilestoneStepTemplates(
    projectId: UUID,
    createdBy: UUID
  ): Seq[(MilestoneStepTemplate, MilestoneStepTemplateVersion)] = {
    val now = tgClock.currentInstant
    DefaultMilestoneStepTemplateService.defaults.map { case (name, description) =>
      val templateId = UUID.randomUUID()
      MilestoneStepTemplate(templateId, projectId, createdBy, now) ->
        MilestoneStepTemplateVersion(UUID.randomUUID(), templateId, name, description, now)
    }
  }
}

object DefaultMilestoneStepTemplateService {

  val Design: String = DefaultMilestoneStepTemplates.Design
  val Prototype: String = DefaultMilestoneStepTemplates.Prototype
  val Development: String = DefaultMilestoneStepTemplates.Development
  val Staging: String = DefaultMilestoneStepTemplates.Staging
  val Production: String = DefaultMilestoneStepTemplates.Production

  /** Name and description of each default step template, in the order they usually happen. */
  val defaults: Seq[(String, String)] = DefaultMilestoneStepTemplates.defaults
}
