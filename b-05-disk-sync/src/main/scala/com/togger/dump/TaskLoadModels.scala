package com.togger.dump

import java.util.UUID

sealed trait TaskLoadTaskStatus

object TaskLoadTaskStatus {
  case object Inserted extends TaskLoadTaskStatus
  case object Merged extends TaskLoadTaskStatus
  case object Unchanged extends TaskLoadTaskStatus
  case object Malformed extends TaskLoadTaskStatus
  case object Failed extends TaskLoadTaskStatus
}

case class TaskLoadTaskResult(
  taskId: UUID,
  taskName: Option[String],
  status: TaskLoadTaskStatus,
  versionsAdded: Int,
  linksAdded: Int,
  logEntriesAdded: Int,
  error: Option[String]
)

case class TaskLoadResult(dumpPath: String, taskCount: Int, taskResults: List[TaskLoadTaskResult])
