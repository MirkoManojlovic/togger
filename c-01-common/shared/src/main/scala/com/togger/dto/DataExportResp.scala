package com.togger.dto

import java.util.UUID

case class DataExportPathResultResp(
  exportPath: String,
  error: Option[String]
) {
  lazy val success: Boolean = error.isEmpty
}

object DataExportPathResultResp {
  def success(exportPath: String) =
    DataExportPathResultResp(exportPath = exportPath, error = None)

  def error(exportPath: String, error: String) =
    DataExportPathResultResp(exportPath = exportPath, error = Some(error))
}

case class DataExportEntityCountResp(entityType: DataExportEntityTypeResp, count: Int)

case class DataExportResp(
  projectId: UUID,
  entityCounts: List[DataExportEntityCountResp],
  results: List[DataExportPathResultResp]
)
