package com.togger.account

import cats.effect.IO
import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import doobie.Transactor
import doobie.implicits._

import java.time.Instant
import java.util.UUID

case class AccountNoCreds(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  accountTypeId: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

class AccountServiceCore(accountRepo: AccountRepo, transactor: Transactor[IO]) {

  def searchPaginated(term: String, page: DbPageRequest): IO[PaginatedResponse[AccountNoCreds]] =
    accountRepo
      .searchPaginated(term, page)
      .map(
        _.mapItem(account =>
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
