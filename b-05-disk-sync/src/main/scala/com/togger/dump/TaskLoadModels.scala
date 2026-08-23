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

sealed trait MappingResolution

object MappingResolution {
  case object Required extends MappingResolution
  case object Conflict extends MappingResolution
  case class Resolved(taskStatusIdMap: Map[UUID, UUID]) extends MappingResolution
}

sealed trait TaskLoadOutcome

object TaskLoadOutcome {
  case class MappingRequired(dumpProjectId: UUID, dumpProjectName: String) extends TaskLoadOutcome
  case class Loaded(result: TaskLoadResult) extends TaskLoadOutcome
}
