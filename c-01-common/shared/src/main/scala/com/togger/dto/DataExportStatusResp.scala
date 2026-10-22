package com.togger.dto

import enumeratum._

import java.util.UUID

sealed trait DataExportSyncStatusResp extends EnumEntry

object DataExportSyncStatusResp extends Enum[DataExportSyncStatusResp] {
  override def values: IndexedSeq[DataExportSyncStatusResp] = findValues

  case object InSync extends DataExportSyncStatusResp
  case object OutOfSync extends DataExportSyncStatusResp
  case object MissingFromExport extends DataExportSyncStatusResp
  case object MissingFromDb extends DataExportSyncStatusResp
  case object Malformed extends DataExportSyncStatusResp
}

case class DataExportTaskStatusResp(
  taskId: UUID,
  taskName: Option[String],
  status: DataExportSyncStatusResp,
  differences: List[String]
)

case class DataExportPathStatusResp(
  exportPath: String,
  taskStatuses: List[DataExportTaskStatusResp]
)

case class DataExportStatusResp(
  projectId: UUID,
  pathStatuses: List[DataExportPathStatusResp]
)
