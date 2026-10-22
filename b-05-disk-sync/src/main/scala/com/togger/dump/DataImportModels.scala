package com.togger.dump

import java.util.UUID

sealed trait DataImportTaskStatus

object DataImportTaskStatus {
  case object Inserted extends DataImportTaskStatus
  case object Merged extends DataImportTaskStatus
  case object Unchanged extends DataImportTaskStatus
  case object Malformed extends DataImportTaskStatus
  case object Failed extends DataImportTaskStatus
}

case class DataImportTaskResult(
  taskId: UUID,
  taskName: Option[String],
  status: DataImportTaskStatus,
  versionsAdded: Int,
  linksAdded: Int,
  logEntriesAdded: Int,
  error: Option[String]
)

case class DataImportResult(importPath: String, taskCount: Int, taskResults: List[DataImportTaskResult])
