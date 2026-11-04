package com.togger.logentry

import java.time.Instant
import java.util.UUID

case class LogEntry(
  id: UUID,
  taskId: UUID,
  createdBy: UUID,
  createdAt: Instant
)

case class LogEntryVersion(
  version: UUID,
  logEntryId: UUID,
  text: String,
  status: LogEntryStatus,
  createdBy: UUID,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(text: String, status: LogEntryStatus): LogEntryVersion =
    this.copy(text = text, status = status)

}

case class LogEntryWithOneVersion(logEntry: LogEntry, version: LogEntryVersion)
case class LogEntryWithVersions(logEntry: LogEntry, versions: Seq[LogEntryVersion]) {
  lazy val latestVersion: LogEntryVersion = versions.head
}
