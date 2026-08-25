package com.togger.dto.accountmapping

import java.time.Instant
import java.util.UUID

case class CreateAccountMappingReq(accountIdDb: UUID, accountIdDump: UUID)

case class AccountMappingResp(
  accountIdDb: UUID,
  accountIdDump: UUID,
  createdAt: Instant,
  updatedAt: Instant
)
