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

case class DataExportEntityStatusResp(
  entityType: DataExportEntityTypeResp,
  entityId: UUID,
  entityName: Option[String],
  status: DataExportSyncStatusResp,
  differences: List[String]
)

case class DataExportPathStatusResp(
  exportPath: String,
  entityStatuses: List[DataExportEntityStatusResp],
  exportConfigId: Option[UUID] = None,
  name: Option[String] = None
)

case class DataExportStatusResp(
  projectId: UUID,
  pathStatuses: List[DataExportPathStatusResp]
)
