package com.togger.account

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.db.{PostgresConfig, TgTestTransactor}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor

import java.sql.{DriverManager, SQLException}
import java.util.UUID
import scala.annotation.tailrec
import scala.util.chaining.scalaUtilChainingOps

/** A private database of one spec, cloned from the migrated `togger-test` database, so specs can run in parallel. */
final class TgTestDatabase private (val name: String, val transactor: HikariTransactor[IO], release: IO[Unit]) {

  def drop(): Unit = {
    release.unsafeRunSync()
    TgTestDatabase.dropDatabase(name)
  }

}

object TgTestDatabase extends TgTestTransactor {

  private val templateName = "togger-test"
  private val objectInUse = "55006"

  /** Postgres allows only one clone of a template at a time. */
  private val cloneLock = new Object

  def create(): TgTestDatabase = {
    val name = s"togger-test-${UUID.randomUUID().toString.take(8)}"
    cloneLock.synchronized(adminStatement(s"""CREATE DATABASE "$name" TEMPLATE "$templateName"""", retries = 100))
    val config = PostgresConfig(
      username = "togger_account",
      password = "togger_password",
      databaseName = name,
      port = 5432,
      connectionTimeout = 3000,
      hostName = "localhost"
    )
    val (transactor, release) = HikariTransactor
      .fromHikariConfig[IO](
        new HikariConfig().tap { hikariConfig =>
          hikariConfig.setDataSource(config.pGSimpleDataSource)
          hikariConfig.setMinimumIdle(0)
          hikariConfig.setMaximumPoolSize(4)
          hikariConfig.setInitializationFailTimeout(-1)
        },
        None
      )
      .allocated
      .unsafeRunSync()
    new TgTestDatabase(name, transactor, release)
  }

  def dropDatabase(name: String): Unit = adminStatement(s"""DROP DATABASE IF EXISTS "$name" WITH (FORCE)""", retries = 0)

  @tailrec
  private def adminStatement(sql: String, retries: Int): Unit = {
    val failure =
      try {
        val connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/postgres", "togger_account", "togger_password")
        try { connection.createStatement().execute(sql); () }
        finally connection.close()
        None
      } catch { case e: SQLException => Some(e) }
    failure match {
      case None => ()
      case Some(e) if retries > 0 && e.getSQLState == objectInUse =>
        Thread.sleep(100)
        adminStatement(sql, retries - 1)
      case Some(e) => throw e
    }
  }

}
