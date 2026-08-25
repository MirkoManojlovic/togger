package com.togger.dto.taskdumpconfig

import java.time.Instant
import java.util.UUID

case class CreateTaskDumpConfigReq(projectId: UUID, name: String, dumpPath: String)

case class UpdateTaskDumpConfigReq(id: UUID, projectId: UUID, name: String, dumpPath: String)

case class TaskDumpConfigResp(
  id: UUID,
  projectId: UUID,
  accountId: UUID,
  name: String,
  dumpPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
