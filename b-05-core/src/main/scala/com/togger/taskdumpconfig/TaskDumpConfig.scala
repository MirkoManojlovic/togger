package com.togger.taskdumpconfig

import java.time.Instant
import java.util.UUID

case class TaskDumpConfig(
  id: UUID,
  projectId: UUID,
  accountId: UUID,
  name: String,
  dumpPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
