package com.togger.dto.taskdumpconfig

import java.time.Instant
import java.util.UUID

case class CreateTaskDumpConfigReq(projectId: UUID, dumpPath: String)

case class UpdateTaskDumpConfigReq(id: UUID, projectId: UUID, dumpPath: String)

case class TaskDumpConfigResp(
  id: UUID,
  projectId: UUID,
  accountId: UUID,
  dumpPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
