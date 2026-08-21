package com.togger.dump

import com.togger.logentry.{LogEntry, LogEntryVersion}
import com.togger.project.Project
import com.togger.task._

import java.time.Instant
import java.util.UUID

case class LinkDumpData(link: Link, versions: List[LinkVersion])

case class LogEntryDumpData(logEntry: LogEntry, versions: List[LogEntryVersion])

case class TaskDumpData(
  task: Task,
  taskVersions: List[TaskVersion],
  links: List[LinkDumpData],
  logEntries: List[LogEntryDumpData]
)

case class ProjectDumpManifest(
  project: Project,
  taskStatuses: List[TaskStatus],
  dumpedAt: Instant,
  taskCount: Int
)

sealed trait TaskDumpSyncStatus

object TaskDumpSyncStatus {
  case object InSync extends TaskDumpSyncStatus
  case object OutOfSync extends TaskDumpSyncStatus
  case object MissingFromDump extends TaskDumpSyncStatus
  case object MissingFromDb extends TaskDumpSyncStatus
  case object Malformed extends TaskDumpSyncStatus
}

case class TaskDumpTaskStatus(taskId: UUID, taskName: Option[String], status: TaskDumpSyncStatus, differences: List[String])
case class TaskDumpPathStatus(dumpPath: String, taskStatuses: List[TaskDumpTaskStatus])
case class TaskDumpStatusResult(pathStatuses: List[TaskDumpPathStatus])
