package com.togger.dump

import java.util.UUID

sealed trait DataImportStatus

object DataImportStatus {
  case object Inserted extends DataImportStatus
  case object Merged extends DataImportStatus
  case object Unchanged extends DataImportStatus
  case object Malformed extends DataImportStatus
  case object Failed extends DataImportStatus
}

case class DataImportEntityResult(
  entityType: DataExportEntityType,
  entityId: UUID,
  entityName: Option[String],
  status: DataImportStatus,
  versionsAdded: Int,
  childrenAdded: Int,
  error: Option[String]
)

object DataImportEntityResult {

  def merged(
    entityType: DataExportEntityType,
    entityId: UUID,
    entityName: Option[String],
    rootInserted: Boolean,
    versionsAdded: Int,
    childrenAdded: Int
  ): DataImportEntityResult = {
    val status =
      if (rootInserted) DataImportStatus.Inserted
      else if (versionsAdded > 0 || childrenAdded > 0) DataImportStatus.Merged
      else DataImportStatus.Unchanged
    DataImportEntityResult(entityType, entityId, entityName, status, versionsAdded, childrenAdded, None)
  }

  def failed(
    entityType: DataExportEntityType,
    entityId: UUID,
    status: DataImportStatus,
    error: String
  ): DataImportEntityResult =
    DataImportEntityResult(entityType, entityId, None, status, 0, 0, Some(error))

}

case class DataImportResult(importPath: String, results: List[DataImportEntityResult])
