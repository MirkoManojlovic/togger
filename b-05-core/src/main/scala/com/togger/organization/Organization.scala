package com.togger.organization

import java.time.Instant
import java.util.UUID

case class Organization(
  id: Long,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class OrganizationAccount(
  organizationId: Long,
  accountId: UUID,
  createdAt: Instant
)
