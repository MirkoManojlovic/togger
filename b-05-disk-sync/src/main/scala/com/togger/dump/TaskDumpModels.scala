package com.togger.dump

import com.togger.logentry.{LogEntry, LogEntryVersion}
import com.togger.project.Project
import com.togger.task._

import java.time.Instant

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
