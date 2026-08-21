package com.togger.dto

import enumeratum._

import java.util.UUID

sealed trait TaskDumpSyncStatusResp extends EnumEntry

object TaskDumpSyncStatusResp extends Enum[TaskDumpSyncStatusResp] {
  override def values: IndexedSeq[TaskDumpSyncStatusResp] = findValues

  case object InSync extends TaskDumpSyncStatusResp
  case object OutOfSync extends TaskDumpSyncStatusResp
  case object MissingFromDump extends TaskDumpSyncStatusResp
  case object MissingFromDb extends TaskDumpSyncStatusResp
  case object Malformed extends TaskDumpSyncStatusResp
}

case class TaskDumpTaskStatusResp(
  taskId: UUID,
  taskName: Option[String],
  status: TaskDumpSyncStatusResp,
  differences: List[String]
)

case class TaskDumpPathStatusResp(
  dumpPath: String,
  taskStatuses: List[TaskDumpTaskStatusResp]
)

case class TaskDumpStatusResp(
  projectId: UUID,
  pathStatuses: List[TaskDumpPathStatusResp]
)
