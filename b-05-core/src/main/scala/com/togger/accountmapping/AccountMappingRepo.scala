package com.togger.accountmapping

import doobie.ConnectionIO

import java.util.UUID

trait AccountMappingRepo {

  def insert(accountMapping: AccountMapping): ConnectionIO[AccountMapping]

  def findByAccountIdDb(accountIdDb: UUID): ConnectionIO[List[AccountMapping]]

  def findByAccountIdDump(accountIdDump: UUID): ConnectionIO[List[AccountMapping]]

  def deleteByIds(accountIdDb: UUID, accountIdDump: UUID): ConnectionIO[Int]

}
