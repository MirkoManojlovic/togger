package com.togger.accountmapping

import cats.effect.IO
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

  def findByAccountIdDb(accountIdDb: UUID): IO[List[AccountMapping]] =
    accountMappingRepo.findByAccountIdDb(accountIdDb).transact(transactor)

  def findByAccountIdDump(accountIdDump: UUID): IO[List[AccountMapping]] =
    accountMappingRepo.findByAccountIdDump(accountIdDump).transact(transactor)

  def deleteByIds(accountIdDb: UUID, accountIdDump: UUID): IO[Int] =
    accountMappingRepo.deleteByIds(accountIdDb, accountIdDump).transact(transactor)

}
