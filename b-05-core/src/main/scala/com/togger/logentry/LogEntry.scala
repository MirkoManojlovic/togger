package com.togger.logentry

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** `createdAt` is when the entry was written, `logDate` the day it is for. `logDate` stays the last field: the
  * repository maps the columns of `log_entry` by position.
  */
case class LogEntry(
  id: UUID,
  taskId: UUID,
  createdBy: UUID,
  createdAt: Instant,
  logDate: LocalDate
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
