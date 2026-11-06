package com.togger.ui.dataimportconfig

import com.togger.dto.DataExportEntityStatusResp
import com.togger.dto.DataExportPathStatusResp
import com.togger.dto.DataExportSyncStatusResp

/** What an import of the checked paths would delete from the project: the entities that the import paths deleted. */
object ImportDeletions {

  private val MaxListed = 10

  def of(paths: List[DataExportPathStatusResp]): List[DataExportEntityStatusResp] =
    paths
      .flatMap(_.entityStatuses)
      .filter(_.status == DataExportSyncStatusResp.PendingDelete)
      .distinctBy(entity => (entity.entityType, entity.entityId))

  def describe(entity: DataExportEntityStatusResp): String =
    s"${entity.entityType.entryName}: ${entity.entityName.getOrElse(entity.entityId.toString.take(8))}"

  /** The first entities by name, then how many more there are. */
  def lines(deletions: List[DataExportEntityStatusResp]): List[String] =
    deletions.take(MaxListed).map(describe) ++
      Option.when(deletions.size > MaxListed)(s"and ${deletions.size - MaxListed} more")

}
