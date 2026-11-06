package com.togger.dump

import java.util.UUID

sealed trait DataImportStatus

object DataImportStatus {
  case object Inserted extends DataImportStatus
  case object Merged extends DataImportStatus
  case object Unchanged extends DataImportStatus
  case object Deleted extends DataImportStatus
  case object Skipped extends DataImportStatus
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

  /** An existing entity whose own fields were updated in place, without a new version. */
  def updated(entityType: DataExportEntityType, entityId: UUID, entityName: Option[String]): DataImportEntityResult =
    DataImportEntityResult(entityType, entityId, entityName, DataImportStatus.Merged, 0, 0, None)

  /** An existing entity that the tree deleted. */
  def deleted(entityType: DataExportEntityType, entityId: UUID): DataImportEntityResult =
    DataImportEntityResult(entityType, entityId, None, DataImportStatus.Deleted, 0, 0, None)

  /** An entity the import left alone because of a tombstone; `reason` tells which one. */
  def skipped(entityType: DataExportEntityType, entityId: UUID, reason: String): DataImportEntityResult =
    DataImportEntityResult(entityType, entityId, None, DataImportStatus.Skipped, 0, 0, Some(reason))

  def failed(
    entityType: DataExportEntityType,
    entityId: UUID,
    status: DataImportStatus,
    error: String
  ): DataImportEntityResult =
    DataImportEntityResult(entityType, entityId, None, status, 0, 0, Some(error))

}

case class DataImportResult(importPath: String, results: List[DataImportEntityResult])
