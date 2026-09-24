package com.togger.organization

import java.time.Instant
import java.util.UUID

case class Organization(
  id: UUID,
  name: String,
  description: String,
  icon: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class OrganizationAccount(
  organizationId: UUID,
  accountId: UUID,
  createdAt: Instant
)
