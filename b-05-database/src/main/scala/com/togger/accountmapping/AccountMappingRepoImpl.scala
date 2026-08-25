package com.togger.accountmapping

import com.togger.db.Util._
import com.togger.accountmapping.AccountMappingRepoImpl.ACCOUNT_MAPPING_TABLE_FRAG
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.fragment.Fragment

import java.util.UUID

class AccountMappingRepoImpl extends AccountMappingRepo {

  override def insert(accountMapping: AccountMapping): ConnectionIO[AccountMapping] = {
    import accountMapping._
    logAroundInfoQuery(
      insertStatement(ACCOUNT_MAPPING_TABLE_FRAG)(fr"""
        (account_id_db, account_id_dump)
        values ($accountIdDb, $accountIdDump)
      """).update
        .withUniqueGeneratedKeys[AccountMapping]("account_id_db", "account_id_dump", "created_at", "updated_at")
    )(
      s"Inserting ${sp(accountMapping)}",
      accountMapping => s"Account mapping inserted ${sp(accountMapping)}"
    )
  }

  override def findByAccountIdDb(accountIdDb: UUID): ConnectionIO[List[AccountMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(ACCOUNT_MAPPING_TABLE_FRAG) ++ fr"where account_id_db = $accountIdDb")
        .query[AccountMapping]
        .to[List]
    )(
      s"Finding account mappings for ${sp(accountIdDb)}",
      mappings => s"${sp(mappings)} found"
    )

  override def findByAccountIdDump(accountIdDump: UUID): ConnectionIO[List[AccountMapping]] =
    logAroundInfoQuery(
      (selectAllStatement(ACCOUNT_MAPPING_TABLE_FRAG) ++ fr"where account_id_dump = $accountIdDump")
        .query[AccountMapping]
        .to[List]
    )(
      s"Finding account mappings for ${sp(accountIdDump)}",
      mappings => s"${sp(mappings)} found"
    )

  override def deleteByIds(accountIdDb: UUID, accountIdDump: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(
      (deleteStatement(ACCOUNT_MAPPING_TABLE_FRAG) ++
        fr"where account_id_db = $accountIdDb and account_id_dump = $accountIdDump").update.run
    )(
      s"Deleting account mapping with ${sp(accountIdDb)} and ${sp(accountIdDump)}",
      _ => s"Account mapping with ${sp(accountIdDb)} and ${sp(accountIdDump)} deleted"
    )

}

object AccountMappingRepoImpl {
  val ACCOUNT_MAPPING_TABLE: String = "account_mapping"
  val ACCOUNT_MAPPING_TABLE_FRAG: Fragment = Fragment.const(ACCOUNT_MAPPING_TABLE)
}
