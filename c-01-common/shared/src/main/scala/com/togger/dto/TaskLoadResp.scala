package com.togger.dto

import enumeratum._

import java.util.UUID

case class TaskLoadReq(dumpPath: String)

sealed trait TaskLoadStatusResp extends EnumEntry

object TaskLoadStatusResp extends Enum[TaskLoadStatusResp] {
  override def values: IndexedSeq[TaskLoadStatusResp] = findValues

  case object Inserted extends TaskLoadStatusResp
  case object Merged extends TaskLoadStatusResp
  case object Unchanged extends TaskLoadStatusResp
  case object Malformed extends TaskLoadStatusResp
  case object Failed extends TaskLoadStatusResp
}

case class TaskLoadTaskResultResp(
  taskId: UUID,
  taskName: Option[String],
  status: TaskLoadStatusResp,
  versionsAdded: Int,
  linksAdded: Int,
  logEntriesAdded: Int,
  error: Option[String]
)

case class TaskLoadResp(
  projectId: UUID,
  dumpPath: String,
  taskCount: Int,
  results: List[TaskLoadTaskResultResp]
)
