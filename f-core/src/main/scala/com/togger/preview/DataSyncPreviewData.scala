package com.togger.preview

import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dto.dataimportconfig.DataImportConfigResp
import com.togger.dto.projectmapping.ProjectMappingResp
import io.circe.generic.auto._

import java.util.UUID

case class PreviewProjectMapping(id: UUID, mapping: ProjectMappingResp)

/** Export configs, import configs and project mappings; the data on disk they point at does not exist in a browser. */
class DataSyncPreviewData(db: PreviewDb, projects: ProjectsPreviewData) {

  val exportConfigs: PreviewCollection[DataExportConfigResp] =
    db.collection[DataExportConfigResp](DataSyncPreviewData.ExportConfigsName, _.id)

  val importConfigs: PreviewCollection[DataImportConfigResp] =
    db.collection[DataImportConfigResp](DataSyncPreviewData.ImportConfigsName, _.id)

  val mappings: PreviewCollection[PreviewProjectMapping] =
    db.collection[PreviewProjectMapping](DataSyncPreviewData.MappingsName, _.id)

  projects.onProjectDeleted { projectId =>
    exportConfigs.deleteWhere(_.projectId == projectId)
    importConfigs.deleteWhere(_.projectId == projectId)
    mappings.deleteWhere(_.mapping.projectIdDb == projectId)
  }
}

object DataSyncPreviewData {
  val ExportConfigsName = "data-export-configs"
  val ImportConfigsName = "data-import-configs"
  val MappingsName = "project-mappings"
}
