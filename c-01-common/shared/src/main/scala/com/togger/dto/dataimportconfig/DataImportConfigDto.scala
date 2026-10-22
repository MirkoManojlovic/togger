package com.togger.dto.dataimportconfig

import java.time.Instant
import java.util.UUID

case class CreateDataImportConfigReq(name: String, projectId: UUID, importPath: String)

case class UpdateDataImportConfigReq(id: UUID, name: String, projectId: UUID, importPath: String)

case class DataImportConfigResp(
  id: UUID,
  name: String,
  projectId: UUID,
  accountId: UUID,
  importPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
