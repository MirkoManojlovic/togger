package com.togger.dataimportconfig

import java.time.Instant
import java.util.UUID

case class DataImportConfig(
  id: UUID,
  name: String,
  importPath: String,
  projectId: UUID,
  accountId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
