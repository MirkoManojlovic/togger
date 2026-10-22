package com.togger.dto

import enumeratum._

import java.util.UUID

case class DataImportReq(importPath: String)

sealed trait DataImportStatusResp extends EnumEntry

object DataImportStatusResp extends Enum[DataImportStatusResp] {
  override def values: IndexedSeq[DataImportStatusResp] = findValues

  case object Inserted extends DataImportStatusResp
  case object Merged extends DataImportStatusResp
  case object Unchanged extends DataImportStatusResp
  case object Malformed extends DataImportStatusResp
  case object Failed extends DataImportStatusResp
}

case class DataImportTaskResultResp(
  taskId: UUID,
  taskName: Option[String],
  status: DataImportStatusResp,
  versionsAdded: Int,
  linksAdded: Int,
  logEntriesAdded: Int,
  error: Option[String]
)

case class DataImportResp(
  projectId: UUID,
  importPath: String,
  taskCount: Int,
  results: List[DataImportTaskResultResp]
)
