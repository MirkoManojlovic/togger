package com.togger.account

import cats.implicits.toTraverseOps
import doobie.ConnectionIO

trait AccountTgDbSpec extends TgDbSpec {
  override val dropTablesQuery: ConnectionIO[Seq[Int]] =
    dropAll(
      Seq(
        AccountSessionRepo.ACCOUNT_SESSION_TABLE,
        AccountRepoImpl.ACCOUNT_TABLE,
        AccountTypeRepo.ACCOUNT_TYPE_TABLE
      )
    ).sequence
}
