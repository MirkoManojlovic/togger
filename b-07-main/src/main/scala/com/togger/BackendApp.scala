package com.togger

import cats.effect._
import com.togger.Constants.DB_NAME
import com.togger.desktop._
import doobie.Transactor
import scribe.format.Formatter
import scribe.{Level, Logger, Logging}

trait TgLogging extends Logging

object BackendApp extends IOApp with TgLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info(s"Raw app input arguments: $args")

    val appTransactor = TgTransactor.desktopTransactor
    val liquibaseTransactor = TgTransactor.liquibaseTransactor
//    val transactor = TgTransactor.dockerTransactor

    val (appEnv, changelogPath) = new AppEnvParser().parse(args)
    logger.info(s"Running App in [env=$appEnv]")
    val appConfigBe = new AppEnvSelector(appEnv).appConfig

    for {
      _ <- setupDb(liquibaseTransactor, appTransactor, changelogPath)
      _ <- setupScribeLogging
      _ <- archiveLogs(appEnv)
      exitCode <- runServer(appTransactor, appConfigBe)
    } yield exitCode
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
        .instance(new TransactorModuleApp(transactor), appConfigBe)
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
    appTransactor: Transactor[IO],
    changelogPath: String
  ): IO[Unit] = {
    val initialSetup = new InitialSetup(liquibaseTransactor)
    val liquibaseOps = new LiquibaseOps(changelogPath)

    for {
      userExists <- initialSetup.checkIfUserExits(Constants.DB_USERNAME)
      _ <-
        if (userExists) {
          for {
            _ <- IO(logger.info("Togger db account exists skipping"))
          } yield ()

        } else {
          for {
            _ <- IO(logger.info("Togger db account doesn't exits. Creating..."))
            _ <- IO(logger.info(s"Creating $DB_NAME database"))
            result <- initialSetup.createDatabase
            _ <- IO(logger.info(s"$DB_NAME database result=$result"))
            _ <- IO(logger.info("Creating togger_account"))
            _ <- initialSetup.createToggerUser
            _ <- IO(logger.info("togger_account created"))
            _ <- IO(logger.info("Adding privileges to togger_account"))
            _ <- initialSetup.makeSuperUser
            _ <- IO(logger.info("Privileges added to togger_account"))
            _ <- IO(logger.info("Running Liquibase"))
            _ <- IO(liquibaseOps.runLiquibase())
            _ <- new CreateAdminAccount(appTransactor).run
          } yield ()
        }
    } yield ()
  }

}
