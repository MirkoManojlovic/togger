package com.togger.account

import cats.effect.IO
import cats.implicits._
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

/** Resolves account ids to the "first last" name shown next to what they created. */
class AccountNameLookup(accountRepo: AccountRepo, transactor: Transactor[IO]) {

  def namesByIds(ids: Set[UUID]): IO[Map[UUID, String]] =
    accountRepo
      .findByIds(ids)
      .map(_.map(account => account.id -> s"${account.firstName} ${account.lastName}").toMap)
      .transact(transactor)
}
