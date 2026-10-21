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
    """**Summary**  
      |Upgrade Node runtime to 22.x on staging
      |
      |**Description**  
      |A detailed description of the task, including the context, requirements and constraints needed to complete it.
      |
      |**Outcome**  
      |Staging environment runs Node 22 with no test regressions.
      |
      |**In scope**  
      |-  Update the Dockerfile
      |-  Refresh the lockfile
      |- Run the full test suite
      |- Update the CI base image
      |
      |**Out of scope**  
      |- Production rollout (tracked separately) 
      |
      |**Dependencies**  
      |- SRE approval for the staging window
      |- Lockfile audit completed
      |- CI base image published
      |
      |**Exit criteria**
      |- All tests pass
      |- CI green for 24 hours
      |- Sign-off from SRE
      |
      |---
      |**Steps**
      |
      |- [ ] Step 1
      |- [ ] Step 2
      |- [ ] Step 3
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
