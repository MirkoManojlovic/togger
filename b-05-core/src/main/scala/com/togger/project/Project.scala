package com.togger.project
import java.time.Instant

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
  accountId: Long,
  createdAt: Instant
)