package com.togger.db

import doobie.postgres.implicits._
import org.postgresql.ds.PGSimpleDataSource

trait TgTestTransactor {

  val pgConfig = PostgresConfig(
    username = "togger_account",
    password = "togger_password",
    databaseName = "togger-test",
    port = 5432,
    connectionTimeout = 3000,
    hostName = "localhost"
  ).pGSimpleDataSource

}

case class PostgresConfig(
  username: String,
  password: String,
  port: Int,
  databaseName: String,
  connectionTimeout: Int,
  hostName: String
) {
  val pGSimpleDataSource: PGSimpleDataSource = {
    val pgDataSource = new org.postgresql.ds.PGSimpleDataSource()
    pgDataSource.setUser(username)
    pgDataSource.setPassword(password)
    pgDataSource.setDatabaseName(databaseName)
    pgDataSource.setPortNumbers(Array(port))
    pgDataSource.setConnectTimeout(connectionTimeout)
    pgDataSource.setServerNames(Array(hostName))
    pgDataSource
  }
}
