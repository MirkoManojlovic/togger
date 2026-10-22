package com.togger.dto.dataexportconfig

import java.time.Instant
import java.util.UUID

case class CreateDataExportConfigReq(projectId: UUID, name: String, exportPath: String)

case class UpdateDataExportConfigReq(id: UUID, projectId: UUID, name: String, exportPath: String)

case class DataExportConfigResp(
  id: UUID,
  projectId: UUID,
  accountId: UUID,
  name: String,
  exportPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
