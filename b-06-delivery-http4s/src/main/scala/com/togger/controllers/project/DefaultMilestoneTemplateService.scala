package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.milestone.{DefaultMilestoneTemplate, DefaultMilestoneTemplates}
import com.togger.milestonetemplate.{MilestoneTemplate, MilestoneTemplateVersion}

import java.util.UUID

class DefaultMilestoneTemplateService(tgClock: TgClock) {

  /** The default milestone templates with their first version and the ordered ids of their step templates.
    *
    * @param stepTemplateIdsByName
    *   the ids of the default step templates of the project, by name
    */
  def createDefaultMilestoneTemplates(
    projectId: UUID,
    createdBy: UUID,
    stepTemplateIdsByName: Map[String, UUID]
  ): Seq[(MilestoneTemplate, MilestoneTemplateVersion, List[UUID])] = {
    val now = tgClock.currentInstant
    DefaultMilestoneTemplateService.defaults.map { default =>
      val templateId = UUID.randomUUID()
      (
        MilestoneTemplate(templateId, projectId, createdBy, now),
        MilestoneTemplateVersion(UUID.randomUUID(), templateId, default.name, default.description, now),
        default.stepNames.flatMap(stepTemplateIdsByName.get)
      )
    }
  }
}

object DefaultMilestoneTemplateService {

  val defaults: Seq[DefaultMilestoneTemplate] = DefaultMilestoneTemplates.defaults
}
