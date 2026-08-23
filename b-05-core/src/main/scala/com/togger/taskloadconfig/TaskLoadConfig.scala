package com.togger.taskloadconfig

import java.time.Instant
import java.util.UUID

case class TaskLoadConfig(
  id: UUID,
  name: String,
  dumpPath: String,
  projectId: UUID,
  accountId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
