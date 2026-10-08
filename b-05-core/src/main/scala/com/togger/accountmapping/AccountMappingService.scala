package com.togger.accountmapping

import cats.effect.IO
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class AccountMappingService(
  accountMappingRepo: AccountMappingRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def create(accountMapping: AccountMapping): IO[AccountMapping] = {
    logger.info(s"Creating ${sp(accountMapping)}")
    accountMappingRepo
      .insert(accountMapping)
      .transact(transactor)
  }

  def findPaginatedByAccountIdDb(
    accountIdDb: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[AccountMapping]] =
    accountMappingRepo.findPaginatedByAccountIdDb(accountIdDb, page).transact(transactor)

  def findPaginatedByAccountIdDump(
    accountIdDump: UUID,
    page: DbPageRequest
  ): IO[PaginatedResponse[AccountMapping]] =
    accountMappingRepo.findPaginatedByAccountIdDump(accountIdDump, page).transact(transactor)

  def deleteByIds(accountIdDb: UUID, accountIdDump: UUID): IO[Int] =
    accountMappingRepo.deleteByIds(accountIdDb, accountIdDump).transact(transactor)

}
