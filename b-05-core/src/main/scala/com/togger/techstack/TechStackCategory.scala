package com.togger.techstack

import java.time.Instant
import java.util.UUID

case class TechStackCategory(
  id: UUID,
  name: String,
  createdAt: Instant,
  updatedAt: Instant
)
