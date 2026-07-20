package com.togger.project
import java.time.Instant
import java.util.UUID

case class Project(
  id: Long,
  organizationId: Long,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class ProjectAccount(
  projectId: Long,
  accountId: UUID,
  createdAt: Instant
)