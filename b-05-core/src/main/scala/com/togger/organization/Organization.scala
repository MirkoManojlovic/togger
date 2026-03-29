package com.togger.organization

import java.time.Instant

case class Organization(
  id: Long,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class OrganizationAccount(
  organizationId: Long,
  accountId: Long,
  createdAt: Instant
)
