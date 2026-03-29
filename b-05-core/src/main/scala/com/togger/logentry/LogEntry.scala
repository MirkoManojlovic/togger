package com.togger.logentry

import java.time.Instant

case class LogEntry(
  id: Long,
  textInternal: String,
  textExternal: String,
  status: LogEntryStatus,
  taskId: Long,
  createdBy: Long,
  createdAt: Instant,
  updatedAt: Instant
)
