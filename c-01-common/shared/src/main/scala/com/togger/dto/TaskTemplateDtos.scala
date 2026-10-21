package com.togger.dto

import java.time.Instant
import java.util.UUID

case class CreateTaskTemplateReq(projectId: UUID, name: String, body: String, taskLabelId: Option[UUID])

case class UpdateTaskTemplateReq(id: UUID, name: String, body: String, taskLabelId: Option[UUID])

case class FindTaskTemplateResp(
  id: UUID,
  projectId: UUID,
  name: String,
  body: String,
  taskLabelId: Option[UUID],
  taskLabelName: Option[String],
  latestVersion: UUID,
  updatedAt: Instant
)

case class TaskTemplateVersionDto(version: UUID, taskTemplateId: UUID, name: String, body: String, createdAt: Instant)
