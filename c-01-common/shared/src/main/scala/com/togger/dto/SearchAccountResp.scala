package com.togger.dto

import java.time.Instant
import java.util.UUID

case class SearchAccountDto(
  id: Long,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: String
) {
  lazy val fullNameAndEmail: String = firstName + lastName + email
  lazy val fullName: String = s"$firstName $lastName"
}

case class SearchAccountResp(
  term: String,
  accounts: Seq[SearchAccountDto]
)

case class AccountNoCreds(
  id: Long,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
