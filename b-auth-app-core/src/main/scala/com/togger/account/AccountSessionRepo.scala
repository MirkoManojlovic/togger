package com.togger.account

import java.time.Instant
import java.util.UUID

import com.togger.account.AccountSessionRepo.ACCOUNT_SESSION_TABLE_FRAG
import com.togger.db.Util._
import com.togger.logging.TgLogging
import doobie.implicits._
import doobie.{ConnectionIO, Fragment}

case class AccountSession(
  id: UUID,
  accountId: UUID,
  sessionToken: String,
  deviceInfo: String,
  createdAt: Instant,
  updatedAt: Instant,
  deletedAt: Option[Instant] = None
)

class AccountSessionRepo extends TgLogging {

  import AccountRepoImpl.passwordCodec
  import doobie.postgres.implicits._

  def insert(accountSession: AccountSession): ConnectionIO[AccountSession] = {
    import accountSession._

    logAroundInfoQuery(
      insertStatement(ACCOUNT_SESSION_TABLE_FRAG)(fr"""
          (account_id, session_token, device_info, created_at, updated_at, deleted_at)
         values ($accountId, $sessionToken, $deviceInfo, $createdAt, $updatedAt, $deletedAt)
           """).update
        .withUniqueGeneratedKeys[AccountSession](
          "id",
          "account_id",
          "session_token",
          "device_info",
          "created_at",
          "updated_at",
          "deleted_at"
        )
    )(
      s"Inserting [accountSession=$accountSession]",
      accountSession => s"AccountSession inserted [accountSession=$accountSession]"
    )
  }

  def findById(id: UUID): ConnectionIO[AccountSession] = {
    logAroundInfoQuery(findByIdStatementUUID(ACCOUNT_SESSION_TABLE_FRAG)(id).query[AccountSession].unique)(
      s"Finding account with [id=$id]",
      accountSession => s"Account session found $accountSession"
    )
  }

  def findByIdNotSoftDeleted(id: UUID): ConnectionIO[AccountSession] = {
    logAroundInfoQuery(findByIdSoftDeletedStatementUUID(ACCOUNT_SESSION_TABLE_FRAG)(id).query[AccountSession].unique)(
      s"Finding account with [id=$id] and not soft deleted",
      accountSession => s"Account session found $accountSession"
    )
  }

  def deleteById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(deleteByIdStatementUUID(ACCOUNT_SESSION_TABLE_FRAG)(id).update.run)(
      s"Deleting accountSession with [id=$id]",
      _ => s"AccountSession with [id=$id] deleted"
    )

  def deleteSoftById(id: UUID): ConnectionIO[Int] =
    logAroundInfoQuery(softDeleteByIdStatementUUID(ACCOUNT_SESSION_TABLE_FRAG)(id).update.run)(
      s"Soft deleting accountSession with [id=$id]",
      _ => s"AccountSession with [id=$id] soft deleted"
    )

  def updateSessionToken(id: UUID, newSessionToken: String, updatedAt: Instant): ConnectionIO[AccountSession] =
    logAroundInfoQuery(
      updateStatementUUID(ACCOUNT_SESSION_TABLE_FRAG)(id)(fr"""
           session_token = $newSessionToken,
           updated_at = $updatedAt""").update
        .withUniqueGeneratedKeys[AccountSession](
          "id",
          "account_id",
          "session_token",
          "device_info",
          "created_at",
          "updated_at",
          "deleted_at"
        )
    )(
      s"Updating accountSession, [id=$id], [newSessionToken=$newSessionToken]",
      accountSession => s"[accountSession=$accountSession] updated"
    )

  def deleteByDate(date: Instant): ConnectionIO[Int] =
    logAroundInfoQuery(
      (fr"delete from" ++ ACCOUNT_SESSION_TABLE_FRAG ++ fr"where created_at < $date").update.run
    )(
      s"Deleting accountSessions older than, [date=$date]",
      numberOfSessionsDeleted => s"[numberOfSessionsDeleted=$numberOfSessionsDeleted]"
    )

  def deleteSoftByDate(date: Instant): ConnectionIO[Int] =
    logAroundInfoQuery(
      (fr"delete from" ++ ACCOUNT_SESSION_TABLE_FRAG ++ fr"where created_at < $date").update.run
    )(
      s"Soft deleting accountSessions older than, [date=$date]",
      numberOfSessionsDeleted => s"[numberOfSessionsDeleted=$numberOfSessionsDeleted]"
    )

  def count(): ConnectionIO[Long] =
    logAroundInfoQuery(
      countStatement(ACCOUNT_SESSION_TABLE_FRAG).query[Long].unique
    )(
      s"Counting account sessions",
      totalNumberOfSessions => s"[totalNumberOfSessions=$totalNumberOfSessions]"
    )

  def countNotSoftDeleted(): ConnectionIO[Long] =
    logAroundInfoQuery(
      softCountStatement(ACCOUNT_SESSION_TABLE_FRAG).query[Long].unique
    )(
      s"Counting account sessions which are not soft deleted",
      totalNumberOfSessions => s"[totalNumberOfSessions=$totalNumberOfSessions]"
    )

  def deleteAll(): ConnectionIO[Int] =
    logAroundInfoQuery(
      deleteStatement(ACCOUNT_SESSION_TABLE_FRAG).update.run
    )(
      s"Deleting all account sessions",
      numberOfSessionsDeleted => s"[numberOfSessionsDeleted=$numberOfSessionsDeleted]"
    )

  def deleteAllSoft(): ConnectionIO[Int] =
    logAroundInfoQuery(
      softDeleteStatement(ACCOUNT_SESSION_TABLE_FRAG).update.run
    )(
      s"Soft deleting all account sessions",
      numberOfSessionsDeleted => s"[numberOfSessionsDeleted=$numberOfSessionsDeleted]"
    )

  def findByToken(token: String): ConnectionIO[AccountSession] =
    logAroundInfoQuery(
      (selectAllStatement(ACCOUNT_SESSION_TABLE_FRAG) ++ fr"where session_token=$token").query[AccountSession].unique
    )(
      s"Finding account with [token=$token]",
      accountSession => s"Account session found $accountSession"
    )

  def findByTokenNotSoftDeleted(token: String): ConnectionIO[AccountSession] =
    logAroundInfoQuery(
      (selectAllStatement(ACCOUNT_SESSION_TABLE_FRAG) ++ fr"where session_token=$token" ++ andNotSoftDeleted)
        .query[AccountSession]
        .unique
    )(
      s"Finding account session with [token=$token] and not soft deleted",
      accountSession => s"Account session found $accountSession"
    )

  def findByTokenWithAccount(sessionToken: String): ConnectionIO[(AccountSession, Account)] =
    logAroundInfoQuery(
      (selectAllStatement(
        ACCOUNT_SESSION_TABLE_FRAG
      ) ++ fr"join account on account_session.account_id = account.id" ++ fr"where session_token=$sessionToken")
        .query[(AccountSession, Account)]
        .unique
    )(
      s"Finding account session and account with [token=$sessionToken]",
      { case (accountSession, account) =>
        s"Account session found [accountSession=$accountSession], [account=$account]"
      }
    )

  def findByTokenWithAccountNotSoftDeleted(sessionToken: String): ConnectionIO[(AccountSession, Account)] =
    logAroundInfoQuery(
      (selectAllStatement(
        ACCOUNT_SESSION_TABLE_FRAG
      ) ++ fr"join account on account_session.account_id = account.id" ++ fr"where session_token=$sessionToken" ++ andNotSoftDeleted)
        .query[(AccountSession, Account)]
        .unique
    )(
      s"Finding account session and account with [token=$sessionToken], not soft deleted",
      { case (accountSession, account) =>
        s"Account session found [accountSession=$accountSession], [account=$account]"
      }
    )

}

object AccountSessionRepo {
  val ACCOUNT_SESSION_TABLE: String = "account_session"
  val ACCOUNT_SESSION_TABLE_FRAG: Fragment = Fragment.const(ACCOUNT_SESSION_TABLE)
}
