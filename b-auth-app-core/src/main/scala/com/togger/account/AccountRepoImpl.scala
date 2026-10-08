package com.togger.account

import com.togger.account.AccountRepoImpl.ACCOUNT_TABLE_FRAG
import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.db.Util._
import com.togger.db.{PaginationSql, TgFindPaginatedQuery}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment, Meta}
import doobie.postgres.implicits._

import java.time.Instant
import java.util.UUID

class AccountRepoImpl extends AccountRepo with TgLogging {
  import AccountRepoImpl.passwordCodec

  override def insert(account: Account): ConnectionIO[Account] = {
    import account._

    logAroundInfoQuery(
      insertStatement(ACCOUNT_TABLE_FRAG)(fr"""
          (first_name, last_name, email, password, account_type_id, verified, enabled, created_at, updated_at)
         values ($firstName, $lastName, $email, $password, $accountTypeId, $verified, $enabled, $createdAt, $updatedAt)
           """).update
        .withUniqueGeneratedKeys[Account](
          "id",
          "first_name",
          "last_name",
          "email",
          "password",
          "account_type_id",
          "verified",
          "enabled",
          "created_at",
          "updated_at"
        )
    )(s"Inserting [account=$account]", account => s"Account inserted [account=$account]")

  }

  override def findById(id: UUID): ConnectionIO[Account] = {
    logAroundInfoQuery((findByIdStatementUUID(ACCOUNT_TABLE_FRAG)(id)).query[Account].unique)(
      s"Finding account with [id=$id]",
      account => s"Account found $account"
    )
  }

  override def findByEmail(email: String): ConnectionIO[Account] = {
    logAroundInfoQuery((selectAllStatement(ACCOUNT_TABLE_FRAG) ++ fr"where email = $email").query[Account].unique)(
      s"Finding account with [email=$email]",
      account => s"Account found $account"
    )
  }

  override def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery((deleteByIdStatementUUID(ACCOUNT_TABLE_FRAG)(id)).update.run)(
      s"Deleting account with [id=$id]",
      _ => s"Account with [id=$id] deleted"
    )

  override def update(account: Account, updatedAt: Instant): ConnectionIO[Account] = {
    logAroundInfoQuery(
      updateStatementUUID(ACCOUNT_TABLE_FRAG)(account.id)(
        fr"""
           first_name = ${account.firstName},
           last_name = ${account.lastName},
           email = ${account.email},
           password = ${account.password},
           account_type_id = ${account.accountTypeId},
           verified = ${account.verified},
           enabled = ${account.verified},
           updated_at = $updatedAt"""
      ).update
        .withUniqueGeneratedKeys[Account](
          "id",
          "first_name",
          "last_name",
          "email",
          "password",
          "account_type_id",
          "verified",
          "enabled",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating [account=$account]",
      account => s"[account=$account] updated"
    )
  }

  override def searchPaginated(term: String, page: DbPageRequest): ConnectionIO[PaginatedResponse[Account]] =
    logAroundInfoQuery(
      TgFindPaginatedQuery[Account](
        pageQuery = request =>
          (selectAllStatement(ACCOUNT_TABLE_FRAG) ++ matchingTerm(term) ++
            PaginationSql.orderBy(fr"last_name", fr"first_name", fr"id") ++
            PaginationSql.limitOffset(request)).query[Account].to[List],
        countQuery = (fr"select count(*) from" ++ ACCOUNT_TABLE_FRAG ++ matchingTerm(term)).query[Long].unique
      ).run(page)
    )(
      s"Finding accounts with ${sp(term)} and ${sp(page)}",
      accounts => s"Account found for ${sp(term)} ${sp(accounts)}"
    )

  // Shared by the page and the count query. Postgres treats backslash as the LIKE escape by default, so the user's own
  // %, _ and \ are escaped with it.
  private def matchingTerm(term: String): Fragment = {
    val pattern = "%" + term.trim.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    fr"where first_name ilike $pattern or last_name ilike $pattern or email ilike $pattern"
  }
}

object AccountRepoImpl {
  val ACCOUNT_TABLE: String = "account"
  val ACCOUNT_TABLE_FRAG: Fragment = Fragment.const(ACCOUNT_TABLE)

  implicit val passwordCodec: Meta[HashedPassword] = Meta[String].imap(HashedPasswordImpl.fromHashedString)(_.value)

}
