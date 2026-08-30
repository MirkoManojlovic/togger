package com.togger.projectmapping

import java.time.Instant
import java.util.UUID

case class ProjectMapping(projectIdDb: UUID, projectIdDump: UUID, createdAt: Instant, updatedAt: Instant)
