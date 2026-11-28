package com.togger.dbmigration

import liquibase.Contexts
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor

import java.sql.Connection
import java.sql.DriverManager
import scala.annotation.nowarn

/** Real databases on the local Postgres for the migration specs. */
object TestDatabases {
  Class.forName("org.postgresql.Driver")
  private val AdminUrl = "jdbc:postgresql://localhost:5432/postgres"

  def create(name: String, changelog: String): Unit = {
    drop(name)
    admin(s"""CREATE DATABASE "$name"""")
    val connection = open(name)
    try {
      val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection))
      new Liquibase(changelog, new ClassLoaderResourceAccessor(), database).update(new Contexts()): @nowarn(
        "cat=deprecation"
      )
    } finally connection.close()
  }

  def drop(name: String): Unit = admin(s"""DROP DATABASE IF EXISTS "$name" WITH (FORCE)""")

  def open(name: String): Connection =
    DriverManager.getConnection(s"jdbc:postgresql://localhost:5432/$name", "togger_account", "togger_password")

  def exists(name: String): Boolean = {
    val connection = DriverManager.getConnection(AdminUrl, "togger_account", "togger_password")
    try {
      val rows = connection.createStatement().executeQuery(s"SELECT 1 FROM pg_database WHERE datname = '$name'")
      rows.next()
    } finally connection.close()
  }

  def execute(name: String, sql: String): Unit = {
    val connection = open(name)
    try { connection.createStatement().execute(sql); () }
    finally connection.close()
  }

  def query(name: String, sql: String): List[List[String]] = {
    val connection = open(name)
    try {
      val rows = connection.createStatement().executeQuery(sql)
      val columns = rows.getMetaData.getColumnCount
      Iterator.continually(rows).takeWhile(_.next()).map(r => (1 to columns).map(r.getString).toList).toList
    } finally connection.close()
  }

  private def admin(sql: String): Unit = {
    val connection = DriverManager.getConnection(AdminUrl, "togger_account", "togger_password")
    try { connection.createStatement().execute(sql); () }
    finally connection.close()
  }
}
