package com.togger.dbmigration

import cats.effect.IO
import cats.effect.Resource
import com.togger.Constants

import java.sql.Connection
import java.sql.DriverManager

/** A plain JDBC connection to one database of the local Postgres, used for the bulk copy between two databases. */
final case class DbConnection(dbName: String) {
  def open: Resource[IO, Connection] =
    Resource.make(
      IO.blocking(DriverManager.getConnection(s"jdbc:postgresql:$dbName", Constants.DB_USERNAME, Constants.DB_PASSWORD))
    )(connection => IO.blocking(connection.close()))
}
