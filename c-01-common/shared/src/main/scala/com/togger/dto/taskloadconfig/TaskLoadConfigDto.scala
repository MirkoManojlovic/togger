package com.togger.dto.taskloadconfig

import java.time.Instant
import java.util.UUID

case class CreateTaskLoadConfigReq(name: String, projectId: UUID, dumpPath: String)

case class UpdateTaskLoadConfigReq(id: UUID, name: String, projectId: UUID, dumpPath: String)

case class TaskLoadConfigResp(
  id: UUID,
  name: String,
  projectId: UUID,
  accountId: UUID,
  dumpPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
