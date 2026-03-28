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
  private def runServer(transactor: Transactor[IO]): IO[ExitCode] =
    TgServer
      .instance(new TransactorModuleApp(transactor))
      .use(_ => IO.never)
      .as(ExitCode.Success)

}
