package com.togger

import cats.effect.{ExitCode, IO, IOApp}
import doobie.Transactor
import scribe.format.Formatter
import scribe.{Level, Logger, Logging}

trait TgLogging extends Logging

object BackendApp extends IOApp with TgLogging {

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info(s"Raw app input arguments: $args")

    val transactor = TgTransactor.desktopTransactor
//    val transactor = TgTransactor.dockerTransactor

//  val appEnv = new AppEnvParser().parse(args)
//  logger.info(s"Running App in [env=$appEnv]")
//  val authAppConfig = new AppEnvSelector(appEnv).authAppConfig
    for {
      _ <- setupScribeLogging
//      exitCode <- runServer(authAppConfig)
      exitCode <- runServer(transactor)
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

//  private def runServer(authAppConfig: AuthAppConfig): IO[ExitCode] =
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

}
