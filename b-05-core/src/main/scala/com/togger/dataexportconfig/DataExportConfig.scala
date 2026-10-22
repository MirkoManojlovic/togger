package com.togger.dataexportconfig

import java.time.Instant
import java.util.UUID

case class DataExportConfig(
  id: UUID,
  projectId: UUID,
  accountId: UUID,
  name: String,
  exportPath: String,
  createdAt: Instant,
  updatedAt: Instant
)
