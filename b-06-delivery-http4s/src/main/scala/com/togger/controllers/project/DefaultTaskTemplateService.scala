package com.togger.controllers.project

import com.togger.common.TgClock
import com.togger.task.{DefaultTaskLabel, TaskLabel}
import com.togger.tasktemplate.{TaskTemplate, TaskTemplateVersion}

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
      DefaultTaskTemplateService.contentOf(label.name).map { case (name, body) =>
        val templateId = UUID.randomUUID()
        TaskTemplate(templateId, projectId, Some(label.id), createdBy, now) ->
          TaskTemplateVersion(UUID.randomUUID(), templateId, name, body, now)
      }
    }
  }
}

object DefaultTaskTemplateService {

  val featureBody: String =
    """## Feature description
      |
      |What should be possible after this task is done, and why.
      |
      |## Feature scope
      |
      |- What is included
      |- What is not included
      |
      |## Definition of done
      |
      |- [ ] Implemented
      |- [ ] Tested
      |- [ ] Documented
      |""".stripMargin

  val bugBody: String =
    """## Steps to reproduce
      |
      |1. Go to ...
      |2. Do ...
      |
      |## Expected
      |
      |What should happen.
      |
      |## Actual
      |
      |What happens instead.
      |""".stripMargin

  def contentOf(labelName: String): Option[(String, String)] =
    labelName match {
      case DefaultTaskLabel.Feature.labelName => Some("Feature" -> featureBody)
      case DefaultTaskLabel.Bug.labelName     => Some("Bug" -> bugBody)
      case _                                  => None
    }
}
