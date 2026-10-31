package com.togger.preview

import com.togger.demo.DemoProjectMapping
import com.togger.dto.dataexportconfig.DataExportConfigResp
import com.togger.dto.dataimportconfig.DataImportConfigResp

/** Seeds export and import configs and project mappings for the demo projects. */
class DataSyncPreviewSeed(
  data: DataSyncPreviewData,
  exportConfigs: List[DataExportConfigResp],
  importConfigs: List[DataImportConfigResp],
  mappings: List[DemoProjectMapping]
) extends PreviewSeeder {

  override def seed(): Unit = {
    exportConfigs.foreach(data.exportConfigs.upsert)
    importConfigs.foreach(data.importConfigs.upsert)
    mappings.foreach(m => data.mappings.upsert(PreviewProjectMapping(m.id, m.mapping)))
  }
}
