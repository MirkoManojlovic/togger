package com.togger.projectmapping

import java.time.Instant
import java.util.UUID

case class ProjectMapping(
  id: UUID,
  projectIdDb: UUID,
  projectIdDump: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
