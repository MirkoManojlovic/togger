package com.togger.projectmapping

import java.time.Instant
import java.util.UUID

case class ProjectMapping(
  id: UUID,
  projectIdDump: UUID,
  projectIdDb: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
