package com.togger.dto

import java.util.UUID

/** The result of importing one import path of a project; `error` is set when the path could not be imported. */
case class DataImportPathResp(importPath: String, result: Option[DataImportResp], error: Option[String])

/** The imports (one per import path, run first) and the export of one project. `exportResult` is empty when the project
  * has no export path configured.
  */
case class DataSyncProjectResp(
  projectId: UUID,
  projectName: String,
  imports: List[DataImportPathResp],
  exportResult: Option[DataExportResp],
  exportError: Option[String]
)

/** Everything one sync did, per project. */
case class DataSyncResp(projects: List[DataSyncProjectResp])
