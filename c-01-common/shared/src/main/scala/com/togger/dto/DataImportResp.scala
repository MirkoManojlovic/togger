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

case class DataImportEntityResultResp(
  entityType: DataExportEntityTypeResp,
  entityId: UUID,
  entityName: Option[String],
  status: DataImportStatusResp,
  versionsAdded: Int,
  childrenAdded: Int,
  error: Option[String]
)

case class DataImportResp(
  projectId: UUID,
  importPath: String,
  entityCounts: List[DataExportEntityCountResp],
  results: List[DataImportEntityResultResp]
)
