package com.togger.desktop

import java.io.File
import java.sql.{DriverManager, SQLException}

import cats.effect.IO
import com.togger.Constants.{DB_NAME, DB_PASSWORD, DB_USERNAME}
import com.togger.TgLogging
import doobie.implicits._
import doobie.{FC, Transactor}
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.DirectoryResourceAccessor
import liquibase.{Contexts, Liquibase}

class LiquibaseOps(changelogPath: String) extends TgLogging {
  private val jdbcUrl = s"jdbc:postgresql:$DB_NAME"

  def runLiquibase(): Unit = {
    val connection = DriverManager.getConnection(jdbcUrl)
    try {
      val database = DatabaseFactory
        .getInstance()
        .findCorrectDatabaseImplementation(new JdbcConnection(connection));

      val changelogFile = new File(s"$changelogPath/liquibase")

      logger.info(s"Finding changelog in ${changelogFile.getAbsolutePath}")

      val resourceAccessor = new DirectoryResourceAccessor(changelogFile)
      val liquibase = new Liquibase("changelog.yaml", resourceAccessor, database)

      liquibase.dropAll()
      liquibase.update(new Contexts())
    } finally {
      if (connection != null) {
        connection.rollback();
        connection.close();
      }
    }
  }

}

class InitialSetup(transactorWithRootAccount: Transactor[IO]) {

  // The reason this transactor exits is because you can't create Database in a transaction
  // Doobie wraps everything in  a transaction and this works
  // createDatabase method still throws `Cannot commit when autoCommit is enabled.`
  // but the db is created so the error is ignored
  private val nonTransactionalTransactor: Transactor[IO] =
    Transactor.before.set(transactorWithRootAccount, FC.unit)

  def checkIfUserExits(account: String): IO[Boolean] = {
    fr"SELECT 1 FROM pg_roles WHERE rolname = $account"
      .query[Int]
      .to[List]
      .map(_.nonEmpty)
      .transact(transactorWithRootAccount)
  }

  def createDatabase: IO[Either[SQLException, Int]] = {
    fr"""CREATE DATABASE "togger-local-development" """.update.run.attemptSql
      .transact(nonTransactionalTransactor)
      .recover { case e =>
        println(s"error ${e}")
        Right(1)
      }
  }

  def createToggerUser: IO[Int] = {
    fr"CREATE USER togger_account_development WITH PASSWORD 'togger_password'".update.run
      .transact(transactorWithRootAccount)
  }

  def grantAllPrivileges: IO[Int] = {
    fr"""GRANT ALL PRIVILEGES ON DATABASE "$DB_NAME" TO togger_account_development""".update.run
      .transact(transactorWithRootAccount)
  }

  def makeSuperUser: IO[Int] = {
    fr"""ALTER ROLE togger_account_development SUPERUSER NOCREATEDB NOCREATEROLE INHERIT LOGIN NOREPLICATION NOBYPASSRLS""".update.run
      .transact(transactorWithRootAccount)
  }

}
