package com.togger

import cats.effect.IO
import doobie.Transactor

object TgTransactor {

  def desktopTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql:togger-local",
      user = "togger_account",
      password = "togger_password",
      logHandler = None
    )

  def dockerTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql://postgres-docker/togger-local",
      user = "togger_account",
      password = "togger_password",
      logHandler = None
    )

}
