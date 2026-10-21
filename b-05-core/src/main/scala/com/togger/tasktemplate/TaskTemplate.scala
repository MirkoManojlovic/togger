package com.togger.tasktemplate

import java.time.Instant
import java.util.UUID

case class TaskTemplate(id: UUID, projectId: UUID, taskLabelId: Option[UUID], createdBy: UUID, createdAt: Instant)

case class TaskTemplateVersion(
  version: UUID,
  taskTemplateId: UUID,
  name: String,
  body: String,
  createdAt: Instant
) {

  def hasSameContentAs(other: TaskTemplateVersion): Boolean =
    name == other.name && body == other.body

}

/** A template with its latest version and the name of the label it is linked to. */
case class TaskTemplateFull(
  template: TaskTemplate,
  latestVersion: TaskTemplateVersion,
  taskLabelName: Option[String]
)
