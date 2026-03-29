package com.togger

import com.togger.TestLoggingJvm.separatorLength
import com.togger.logging.TgLoggingExtra
import scribe.format.Formatter
import scribe.writer.ConsoleWriter
import scribe.{Level, Logger, Logging}

trait TestLoggingJvm extends Logging with TgLoggingExtra {
  import TestLoggingJvm.testLogger

  override protected def logger: Logger =
    testLogger

  // The reason for this is that if there is a test where no logging is being used then the test log config will not get
  // loaded up. So to make this work, the logger needs to be called first. But the good thing is it is a lazy val in
  // an object so it will only get called once :)
  testLogger

  implicit class LogResultSeqOps[A](items: Iterable[A]) {
    def logDebugSeq(title: String = ""): Unit = {
      logger.debug(s"BEGIN $title" + ("-" * separatorLength))
      items.foreach(item => logger.debug("🐞 " + item.toString))
      logger.debug(s"END $title" + ("-" * separatorLength))
    }

    def logInfoSeq(title: String = ""): Unit = {
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

object TestLoggingJvm {

  lazy val separatorLength = 120

  lazy val testLogger: Logger =
    Logger.root
      .clearHandlers()
      .withMinimumLevel(Level.Info)
//      .withHandler(formatter = Formatter.compact, writer = FileWriter("logs" / "test.log"))
      .withHandler(formatter = Formatter.compact, writer = ConsoleWriter)
      .replace()

}
