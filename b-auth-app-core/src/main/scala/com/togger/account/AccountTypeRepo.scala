package com.togger.account

import java.time.Instant
import java.util.UUID

import com.togger.account.AccountTypeRepo.ACCOUNT_TYPE_TABLE_FRAG
import com.togger.db.Util._
import com.togger.logging.TgLogging
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Fragment}

case class AccountType(
  id: UUID,
  name: String,
  createdAt: Instant,
  updatedAt: Instant
)

class AccountTypeRepo extends TgLogging {

  def insert(accountType: AccountType): ConnectionIO[AccountType] = {
    import accountType._

    logAroundInfoQuery(
      insertStatement(ACCOUNT_TYPE_TABLE_FRAG)(fr"""
          (name, created_at, updated_at)
         values ($name,  $createdAt, $updatedAt)
           """).update
        .withUniqueGeneratedKeys[AccountType](
          "id",
          "name",
          "created_at",
          "updated_at"
        )
    )(s"Inserting [accountType=$accountType]", accountType => s"AccountType type inserted [accountType=$accountType]")

  }

  def findById(id: UUID): ConnectionIO[AccountType] = {
    logAroundInfoQuery(findByIdStatementUUID(ACCOUNT_TYPE_TABLE_FRAG)(id).query[AccountType].unique)(
      s"Finding accountType with [id=$id]",
      accountType => s"AccountType found $accountType"
    )
  }

  def findByName(name: String): ConnectionIO[AccountType] = {
    logAroundInfoQuery(
      (selectAllStatement(ACCOUNT_TYPE_TABLE_FRAG) ++ fr"where name = $name").query[AccountType].unique
    )(
      s"Finding accountType with [name=$name]",
      accountType => s"AccountType found $accountType"
    )
  }

  def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(ACCOUNT_TYPE_TABLE_FRAG)(id).update.run)(
      s"Deleting accountType with [id=$id]",
      _ => s"AccountType with [id=$id] deleted"
    )

  def update(accountType: AccountType, updatedAt: Instant): ConnectionIO[AccountType] = {
    logAroundInfoQuery(
      updateStatementUUID(ACCOUNT_TYPE_TABLE_FRAG)(accountType.id)(
        fr"""
           name = ${accountType.name},
           updated_at = $updatedAt"""
      ).update
        .withUniqueGeneratedKeys[AccountType](
          "id",
          "name",
          "created_at",
          "updated_at"
        )
    )(
      s"Updating [accountType=$accountType]",
      accountType => s"[accountType=$accountType] updated"
    )
  }

}

object AccountTypeRepo {
  val ACCOUNT_TYPE_TABLE: String = "account_type"
  val ACCOUNT_TYPE_TABLE_FRAG: Fragment = Fragment.const(ACCOUNT_TYPE_TABLE)

}
