package com.togger

import cats.effect.{IO, Resource}
import com.togger.Constants.{DB_PASSWORD, DB_USERNAME}
import com.zaxxer.hikari.HikariConfig
import doobie.Transactor
import doobie.hikari.HikariTransactor

object TgTransactor {

  private val PoolSize = 10

  /** Pooled, so a transaction reuses an open connection instead of paying for a new one each time. */
  def desktopTransactor(dbMode: DbMode): Resource[IO, Transactor[IO]] =
    pooledTransactor(s"jdbc:postgresql:${dbMode.dbName}")

  def dockerTransactor(dbMode: DbMode): Resource[IO, Transactor[IO]] =
    pooledTransactor(s"jdbc:postgresql://postgres-docker/${dbMode.dbName}")

  private def pooledTransactor(url: String): Resource[IO, Transactor[IO]] = {
    val config = new HikariConfig()
    config.setDriverClassName("org.postgresql.Driver")
    config.setJdbcUrl(url)
    config.setUsername(DB_USERNAME)
    config.setPassword(DB_PASSWORD)
    config.setMaximumPoolSize(PoolSize)
    config.setMinimumIdle(1)
    config.setInitializationFailTimeout(-1) // the pool is built before setupDb has created the database on a first run
    HikariTransactor.fromHikariConfig[IO](config, None)
  }

  val liquibaseTransactor: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = "jdbc:postgresql:postgres",
      user = "postgres",
      password = "",
      logHandler = None
    )

}
