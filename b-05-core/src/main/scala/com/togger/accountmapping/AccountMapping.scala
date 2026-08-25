package com.togger.accountmapping

import java.time.Instant
import java.util.UUID

case class AccountMapping(accountIdDb: UUID, accountIdDump: UUID, createdAt: Instant, updatedAt: Instant)
