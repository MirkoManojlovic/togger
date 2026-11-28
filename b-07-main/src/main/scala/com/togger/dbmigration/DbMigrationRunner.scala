package com.togger.dbmigration

import cats.effect.IO
import com.togger.TgLogging
import com.togger.desktop.InitialSetup

import java.sql.Connection

/** Creates the database of an app version, applies the schema and copies the data of the previous version into it.
  *
  * The copy is complete once the version is recorded in `db_migration_log`; a database without that row is an
  * unfinished one and is dropped and built again.
  */
class DbMigrationRunner(
  initialSetup: InitialSetup,
  applySchema: String => IO[Unit],
  migrations: List[DbMigration] = DbMigrations.all
) extends TgLogging {

  def run(dbName: String, version: SemVer): IO[Unit] =
    for {
      exists <- initialSetup.checkIfDatabaseExists(dbName)
      complete <- if (exists) isMigrated(dbName, version) else IO.pure(false)
      _ <-
        if (complete) IO(logger.info(s"Database $dbName is migrated, skipping"))
        else rebuild(dbName, version, exists)
    } yield ()

  private def rebuild(dbName: String, version: SemVer, exists: Boolean): IO[Unit] =
    for {
      _ <-
        if (exists) IO(logger.warn(s"Database $dbName is unfinished, dropping it")) *> initialSetup.dropDatabase(dbName)
        else IO.unit
      _ <- IO(logger.info(s"Creating database $dbName"))
      _ <- initialSetup.createDatabase(dbName)
      created <- initialSetup.checkIfDatabaseExists(dbName)
      _ <- IO.raiseUnless(created)(new IllegalStateException(s"Database $dbName could not be created"))
      _ <- applySchema(dbName)
      _ <- copyPreviousData(dbName, version)
      _ <- markMigrated(dbName, version)
    } yield ()

  private def copyPreviousData(dbName: String, version: SemVer): IO[Unit] =
    for {
      existing <- initialSetup.listDatabases
      previous = DbNames.previousOf(version, existing.filterNot(_ == dbName))
      _ <- previous match {
        case None         => IO(logger.info("No previous database, starting with an empty one"))
        case Some(source) => migrateFrom(source, dbName, version)
      }
    } yield ()

  private def migrateFrom(source: String, dbName: String, version: SemVer): IO[Unit] =
    migrations.find(_.version == version) match {
      case None => IO.raiseError(new IllegalStateException(s"There is no DbMigration for version ${version.render}"))
      case Some(migration) =>
        IO(logger.info(s"Migrating the data of $source into $dbName")) *>
          migration.migrate(DbConnection(source), DbConnection(dbName)).onError { case e =>
            IO(logger.error(s"Migration of $source into $dbName failed: ${e.getMessage}")) *> initialSetup.dropDatabase(
              dbName
            )
          }
    }

  private def isMigrated(dbName: String, version: SemVer): IO[Boolean] =
    DbConnection(dbName).open.use(connection => IO.blocking(hasMarker(connection, version))).handleError(_ => false)

  private def hasMarker(connection: Connection, version: SemVer): Boolean = {
    val statement = connection.prepareStatement(
      "SELECT 1 FROM information_schema.tables WHERE table_name = 'db_migration_log' AND table_schema = 'public'"
    )
    try
      if (!statement.executeQuery().next()) false
      else {
        val marker = connection.prepareStatement("SELECT 1 FROM db_migration_log WHERE version = ?")
        marker.setString(1, version.render)
        try marker.executeQuery().next()
        finally marker.close()
      }
    finally statement.close()
  }

  private def markMigrated(dbName: String, version: SemVer): IO[Unit] =
    DbConnection(dbName).open.use { connection =>
      IO.blocking {
        val statement =
          connection.prepareStatement("INSERT INTO db_migration_log (version, migrated_at) VALUES (?, now())")
        try { statement.setString(1, version.render); statement.executeUpdate(); () }
        finally statement.close()
      }
    }
}
