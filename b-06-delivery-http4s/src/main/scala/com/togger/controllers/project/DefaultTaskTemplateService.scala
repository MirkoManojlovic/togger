package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.DefaultTaskTemplates
import com.togger.task.TaskLabel
import com.togger.tasktemplate.TaskTemplate
import com.togger.tasktemplate.TaskTemplateVersion

import java.util.UUID

class DefaultTaskTemplateService(tgClock: TgClock) {

  /** A template with its first version for each of the labels that has a default one, in the order of the labels. */
  def createDefaultTaskTemplates(
    projectId: UUID,
    createdBy: UUID,
    labels: Seq[TaskLabel]
  ): Seq[(TaskTemplate, TaskTemplateVersion)] = {
    val now = tgClock.currentInstant
    labels.flatMap { label =>
      DefaultTaskTemplates.contentOf(label.name).map { case (name, body) =>
        val templateId = UUID.randomUUID()
        TaskTemplate(templateId, projectId, Some(label.id), createdBy, now) ->
          TaskTemplateVersion(UUID.randomUUID(), templateId, name, body, now)
      }
    }
  }
}
