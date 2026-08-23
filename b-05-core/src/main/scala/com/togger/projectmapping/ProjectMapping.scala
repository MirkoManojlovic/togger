package com.togger.projectmapping

import java.time.Instant
import java.util.UUID

case class ProjectMapping(
  id: UUID,
  projectIdOrigin: UUID,
  projectIdLoaded: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
