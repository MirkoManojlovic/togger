package com.togger.projectlabel

import java.time.Instant
import java.util.UUID

case class ProjectLabelCategory(
  id: UUID,
  name: String,
  createdAt: Instant,
  updatedAt: Instant
)
