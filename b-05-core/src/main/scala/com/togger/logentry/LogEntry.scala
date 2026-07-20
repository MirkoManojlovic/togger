package com.togger.logentry

import java.time.Instant
import java.util.UUID

case class LogEntry(
  id: UUID,
  textInternal: String,
  textExternal: String,
  status: LogEntryStatus,
  taskId: Long,
  createdBy: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
