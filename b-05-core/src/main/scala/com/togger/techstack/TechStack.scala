package com.togger.techstack

import java.time.Instant
import java.util.UUID

case class TechStack(
  id: UUID,
  name: String,
  description: String,
  categoryId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
