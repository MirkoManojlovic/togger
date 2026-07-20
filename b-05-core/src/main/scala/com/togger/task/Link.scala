package com.togger.task

import java.time.Instant
import java.util.UUID

case class Link(
  id: UUID,
  name: String,
  url: String,
  taskId: Long,
  createdAt: Instant,
  updatedAt: Instant
)
