package com.togger.controllers.accountmapping

import com.togger.accountmapping.{AccountMapping, AccountMappingEndpoints, AccountMappingService}
import com.togger.common.{TgClock, TgJsonSuccess}
import com.togger.dto.accountmapping.AccountMappingResp

class AccountMappingController(
  accountMappingEndpoints: AccountMappingEndpoints,
  accountMappingService: AccountMappingService,
  tgClock: TgClock
) {

  import accountMappingEndpoints._

  private def toResp(accountMapping: AccountMapping): AccountMappingResp =
    AccountMappingResp(
      accountIdDb = accountMapping.accountIdDb,
      accountIdDump = accountMapping.accountIdDump,
      createdAt = accountMapping.createdAt,
      updatedAt = accountMapping.updatedAt
    )

  val save = saveEndpoint.serverLogic { _ => dto =>
    val now = tgClock.currentInstant

    val accountMapping = AccountMapping(
      accountIdDb = dto.accountIdDb,
      accountIdDump = dto.accountIdDump,
      createdAt = now,
      updatedAt = now
    )

    accountMappingService
      .create(accountMapping)
      .map(saved => TgJsonSuccess.justBodyRight(toResp(saved)))
  }

  val findByAccountIdDb =
    findByAccountIdDbEndpoint.serverLogic { _ => accountIdDb =>
      accountMappingService
        .findByAccountIdDb(accountIdDb)
        .map(mappings => TgJsonSuccess.justBodyRight(mappings.map(toResp)))
    }

  val findByAccountIdDump =
    findByAccountIdDumpEndpoint.serverLogic { _ => accountIdDump =>
      accountMappingService
        .findByAccountIdDump(accountIdDump)
        .map(mappings => TgJsonSuccess.justBodyRight(mappings.map(toResp)))
    }

  val delete = deleteEndpoint.serverLogic { _ =>
    { case (accountIdDb, accountIdDump) =>
      accountMappingService
        .deleteByIds(accountIdDb, accountIdDump)
        .map(_ => TgJsonSuccess.justMessageRight("Account mapping deleted successfully"))
    }
  }

}
