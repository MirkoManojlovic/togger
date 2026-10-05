package com.togger.projectlabel

import java.time.Instant
import java.util.UUID

case class ProjectLabel(
  id: UUID,
  name: String,
  description: String,
  categoryId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
