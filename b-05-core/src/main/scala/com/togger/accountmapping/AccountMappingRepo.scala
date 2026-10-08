package com.togger.accountmapping

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait AccountMappingRepo {

  def insert(accountMapping: AccountMapping): ConnectionIO[AccountMapping]

  def findPaginatedByAccountIdDb(
    accountIdDb: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[AccountMapping]]

  def findPaginatedByAccountIdDump(
    accountIdDump: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[AccountMapping]]

  def deleteByIds(accountIdDb: UUID, accountIdDump: UUID): ConnectionIO[Int]

}
