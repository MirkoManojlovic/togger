package com.togger.auth

import java.time.Instant
import java.util.UUID

case class AuthAccount(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  createdAt: Instant,
  updatedAt: Instant,
  deviceInfo: String,
  sessionCreatedAt: Instant,
  sessionUpdatedAt: Instant
)
