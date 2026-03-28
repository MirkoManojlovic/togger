package com.togger.auth

import java.time.Instant

case class AuthAccount(
  id: Long,
  firstName: String,
  lastName: String,
  email: String,
  createdAt: Instant,
  updatedAt: Instant,
  deviceInfo: String,
  sessionCreatedAt: Instant,
  sessionUpdatedAt: Instant
)
