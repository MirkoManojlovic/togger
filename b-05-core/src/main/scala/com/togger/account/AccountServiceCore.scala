package com.togger.account

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import doobie.Transactor
import doobie.implicits._

case class AccountNoCreds(
  id: Long,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

class AccountServiceCore(accountRepo: AccountRepo, transactor: Transactor[IO]) {

  def searchByFirstNameLastNameEmail(term: String): IO[Seq[AccountNoCreds]] = {
    accountRepo
      .findByFirstNameLastNameOrEmail(term)
      .map(accounts =>
        accounts.map(account =>
          AccountNoCreds(
            id = account.id,
            firstName = account.firstName,
            lastName = account.lastName,
            email = account.email,
            accountTypeId = account.accountTypeId,
            createdAt = account.createdAt,
            updatedAt = account.updatedAt
          )
        )
      )
      .transact(transactor)

  }

}
