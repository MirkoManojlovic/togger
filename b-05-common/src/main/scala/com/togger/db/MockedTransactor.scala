package com.togger.db

import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import doobie.ConnectionIO
import doobie.Transactor
import doobie.util.transactor.Transactor.Aux

import java.sql.Connection

trait MockedTransactor {
  private val emptyConnection = ().pure[ConnectionIO]
  private val strategy =
    doobie.util.transactor.Strategy(emptyConnection, emptyConnection, emptyConnection, emptyConnection)
  val transactorMock: Aux[IO, Connection] = Transactor.fromConnection[IO](null, None).copy(strategy0 = strategy)
}
