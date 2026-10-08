package com.togger.account

import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

/** An account is an admin when its account type is named [[AdminCheck.AdminAccountTypeName]]; an account that does not
  * exist is not.
  */
class AccountTypeAdminCheck(accountRepo: AccountRepo, accountTypeRepo: AccountTypeRepo, transactor: Transactor[IO])
    extends AdminCheck {

  override def isAdmin(accountId: UUID): IO[Boolean] =
    (for {
      account <- accountRepo.findById(accountId)
      accountType <- accountTypeRepo.findById(account.accountTypeId)
    } yield accountType.name == AdminCheck.AdminAccountTypeName).attempt
      .map(_.getOrElse(false))
      .transact(transactor)
}
