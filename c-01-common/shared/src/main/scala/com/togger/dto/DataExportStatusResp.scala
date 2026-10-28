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

/** A field that differs between the disk and the database; `None` when the field is absent on that side. */
case class DataExportFieldDiffResp(field: String, disk: Option[String], database: Option[String])

case class DataExportEntityStatusResp(
  entityType: DataExportEntityTypeResp,
  entityId: UUID,
  entityName: Option[String],
  status: DataExportSyncStatusResp,
  differences: List[String],
  fieldDiffs: List[DataExportFieldDiffResp] = Nil
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
