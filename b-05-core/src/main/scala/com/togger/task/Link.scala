package com.togger.task

import java.time.Instant

case class Link(
  id: Long,
  name: String,
  url: String,
  taskId: Long,
  createdAt: Instant,
  updatedAt: Instant
)
