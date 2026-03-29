package com.togger.logging

import com.togger.logging.TgLoggingExtra
import com.togger.logging.TestLoggingJs.separatorLength
import scribe.format.Formatter
import scribe.writer.ConsoleWriter
import scribe.{Level, Logger, Logging}

trait TestLoggingJs extends Logging with TgLoggingExtra {
  import TestLoggingJs.testLogger

  override protected def logger: Logger =
    testLogger

  // The reason for this is that if there is a test where no logging is being used then the test log config will not get
  // loaded up. So to make this work, the logger needs to be called first. But the good thing is it is a lazy val in
  // an object so it will only get called once :)
  testLogger

  implicit class LogResultSeqOps[A](items: Iterable[A]) {
    def logDebugSeq(title: String = ""): Unit = {
      println() // This adds a new line without printing time,date, thread...
      logger.debug(s"BEGIN $title" + ("-" * separatorLength))
      items.foreach(item => logger.debug("🐞 " + item.toString))
      logger.debug(s"END $title" + ("-" * separatorLength))
    }

    def logInfoSeq(title: String = ""): Unit = {
      println() // This adds a new line without printing time,date, thread...
      logger.info(s"BEGIN $title" + ("-" * separatorLength))
      logger.info(title)
      items.foreach(item => logger.info("ℹ️ " + item.toString))
      logger.info(s"END $title" + ("-" * separatorLength))
    }
  }

  implicit class LogResultOps(item: String) {
    def logDebug(): Unit =
      logger.debug("🐞 " + item)

    def logInfo(): Unit =
      logger.info("ℹ️ " + item)
  }
}

object TestLoggingJs {

  lazy val separatorLength = 120

  lazy val testLogger: Logger =
    Logger.root
      .clearHandlers()
      .withMinimumLevel(Level.Debug)
      .withHandler(formatter = Formatter.compact, writer = ConsoleWriter)
      .replace()

}
