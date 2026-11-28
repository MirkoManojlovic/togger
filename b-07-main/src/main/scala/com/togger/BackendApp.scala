package com.togger

import cats.effect._
import com.togger.dbmigration.DbMigrationRunner
import com.togger.dbmigration.SemVer
import com.togger.desktop._
import doobie.Transactor
import scribe.Level
import scribe.Logger
import scribe.Logging
import scribe.format.Formatter

trait TgLogging extends Logging

object BackendApp extends IOApp with TgLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info(s"Raw app input arguments: $args")

    val liquibaseTransactor = TgTransactor.liquibaseTransactor
//    val transactor = TgTransactor.dockerTransactor

    val (appEnv, changelogPath, dbMode) = new AppEnvParser().parse(args)
    val dbName = dbMode.dbNameFor(BuildInfo.version)
    logger.info(s"Running App in [env=$appEnv] [dbMode=$dbMode] [db=$dbName] [version=${BuildInfo.version}]")
    val appConfigBe = new AppEnvSelector(appEnv).appConfig

    TgTransactor.desktopTransactor(dbName).use { appTransactor =>
      for {
        _ <- setupDb(liquibaseTransactor, changelogPath, dbMode, dbName)
        _ <- new CreateAdminAccount(appTransactor).run
        _ <- setupScribeLogging
        _ <- archiveLogs(appEnv)
        exitCode <- runServer(appTransactor, appConfigBe)
      } yield exitCode
    }
  }

  private def setupScribeLogging: IO[Logger] =
    IO(
      Logger.root
        .clearHandlers()
        .withMinimumLevel(Level.Debug)
        .withHandler(formatter = Formatter.compact)
        .replace()
    )

  private def archiveLogs(appEnv: AppEnv): IO[Unit] =
    appEnv match {
      case AppEnv.Web =>
        logger.info("Running in web mode, not archiving logs")
        IO.unit
      case AppEnv.Desktop =>
        logger.info("Running in desktop mode, archiving logs")
        IO(new LogArchive().run())
    }

  private def runServer(transactor: Transactor[IO], appConfigBe: AppConfigBe): IO[ExitCode] =
    for {
      stopSignal <- Deferred[IO, Unit]
      serverResult <- TgServer
        .instance(new TransactorModuleApp(transactor))
        .use(_ => runServer(stopSignal, appConfigBe))
        .map(_ => logger.info("Stoping the app"))
        .as(ExitCode.Success)
    } yield serverResult

  private def runServer(stopSignal: Deferred[IO, Unit], appConfigBe: AppConfigBe): IO[IO[Unit]] = {
    if (appConfigBe.isDesktopMode) runServerDesktop(stopSignal)
    else runServerWeb
  }

  private def runServerWeb: IO[IO[Unit]] =
    IO.never

  private def runServerDesktop(stopSignal: Deferred[IO, Unit]): IO[IO[Unit]] = {
    val checkDesktopAppRunningCondition = new CheckDesktopAppRunningCondition()
    val checkDesktopRunning = new CheckDesktopAppRunning

    for {
      _ <- checkDesktopRunning.run(() => checkDesktopAppRunningCondition.isRunning, stopSignal)
      _ <- stopSignal.get.map(_ => logger.info("Stop signal received, stoping the app"))
    } yield IO.unit
  }

  private def setupDb(
    liquibaseTransactor: Transactor[IO],
    changelogPath: String,
    dbMode: DbMode,
    dbName: String
  ): IO[Unit] = {
    val initialSetup = new InitialSetup(liquibaseTransactor)
    val liquibaseOps = (name: String) => IO(new LiquibaseOps(changelogPath, name).runLiquibase())

    for {
      _ <- ensureToggerUser(initialSetup)
      _ <- versionedDb(dbMode) match {
        case Some(version) => new DbMigrationRunner(initialSetup, liquibaseOps).run(dbName, version)
        case None          => setupUnversionedDb(initialSetup, liquibaseOps, dbName)
      }
    } yield ()
  }

  /** Only prod with a release version has a database per version. */
  private def versionedDb(dbMode: DbMode): Option[SemVer] =
    if (dbMode == DbMode.Prod) SemVer.parse(BuildInfo.version) else None

  private def ensureToggerUser(initialSetup: InitialSetup): IO[Unit] =
    initialSetup.checkIfUserExits(Constants.DB_USERNAME).flatMap {
      case true => IO(logger.info("togger_account exists, reusing it"))
      case false =>
        for {
          _ <- IO(logger.info("Creating togger_account"))
          _ <- initialSetup.createToggerUser
          _ <- initialSetup.makeSuperUser
          _ <- IO(logger.info("togger_account created with privileges"))
        } yield ()
    }

  private def setupUnversionedDb(
    initialSetup: InitialSetup,
    applySchema: String => IO[Unit],
    dbName: String
  ): IO[Unit] =
    initialSetup.checkIfDatabaseExists(dbName).flatMap {
      case true => IO(logger.info(s"Togger db $dbName exists, skipping"))
      case false =>
        for {
          _ <- IO(logger.info(s"Creating $dbName database"))
          result <- initialSetup.createDatabase(dbName)
          _ <- IO(logger.info(s"$dbName database result=$result"))
          _ <- applySchema(dbName)
        } yield ()
    }

}
