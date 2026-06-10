package com.togger

import cats.effect.IO
import com.togger.Constants.{DB_NAME, DB_PASSWORD, DB_USERNAME}
import doobie.Transactor

object TgTransactor {

  def desktopTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql:$DB_NAME",
      user = DB_USERNAME,
      password = DB_PASSWORD,
      logHandler = None
    )

  def dockerTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql://postgres-docker/$DB_NAME",
      user = DB_USERNAME,
      password = DB_PASSWORD,
      logHandler = None
    )

  val liquibaseTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql:postgres",
      user = "postgres",
      password = "",
      logHandler = None
    )

}
