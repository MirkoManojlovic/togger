package com.togger.desktop

import cats.effect.Deferred
import cats.effect.IO
import com.togger.TgLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

class CheckDesktopAppRunning(checkInterval: FiniteDuration = 30.seconds) extends TgLogging {

  def run(fn: () => Boolean, stopSignal: Deferred[IO, Unit]): IO[Unit] =
    loop(fn, stopSignal)

  private def loop(fn: () => Boolean, stopSignal: Deferred[IO, Unit]): IO[Unit] =
    for {
      _ <- IO.sleep(checkInterval)
      _ <- IO(logger.info("Checking if app is running"))
      isRunning = fn()
      _ <-
        if (isRunning) {
          for {
            _ <- IO(logger.info("Desktop app is running"))
            _ <- loop(fn, stopSignal)
          } yield ()

        } else {
          for {
            _ <- IO(logger.info("Desktop app is not running"))
            _ <- stopSignal.complete(())
          } yield ()
        }
    } yield ()

}
