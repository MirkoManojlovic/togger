package com.togger.accountmapping

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
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

  override def findPaginatedByAccountIdDb(
    accountIdDb: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[AccountMapping]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[AccountMapping](
        pageQuery = request =>
          (selectAllStatement(ACCOUNT_MAPPING_TABLE_FRAG) ++ fr"where account_id_db = $accountIdDb" ++
            PaginationSql.orderBy(fr"account_id_dump") ++
            PaginationSql.limitOffset(request)).query[AccountMapping].to[List],
        countQuery = (fr"select count(*) from" ++ ACCOUNT_MAPPING_TABLE_FRAG ++
          fr"where account_id_db = $accountIdDb").query[Long].unique
      ).run(page)
    )(
      s"Finding account mappings of ${sp(accountIdDb)} with ${sp(page)}",
      mappings => s"Account mappings found ${sp(mappings)} for ${sp(accountIdDb)}"
    )

  override def findPaginatedByAccountIdDump(
    accountIdDump: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[AccountMapping]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[AccountMapping](
        pageQuery = request =>
          (selectAllStatement(ACCOUNT_MAPPING_TABLE_FRAG) ++ fr"where account_id_dump = $accountIdDump" ++
            PaginationSql.orderBy(fr"account_id_db") ++
            PaginationSql.limitOffset(request)).query[AccountMapping].to[List],
        countQuery = (fr"select count(*) from" ++ ACCOUNT_MAPPING_TABLE_FRAG ++
          fr"where account_id_dump = $accountIdDump").query[Long].unique
      ).run(page)
    )(
      s"Finding account mappings of ${sp(accountIdDump)} with ${sp(page)}",
      mappings => s"Account mappings found ${sp(mappings)} for ${sp(accountIdDump)}"
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
