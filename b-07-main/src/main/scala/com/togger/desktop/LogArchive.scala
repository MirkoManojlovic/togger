package com.togger.desktop

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.togger.TgLogging

import scala.util.Try

object CompactLogs1 extends App {
  new LogArchive().run()
}

class LogArchive extends TgLogging {

  val logPath = os.home / "togger" / "logs"
  val formatter = DateTimeFormatter.ofPattern("YYYYMMdd")
  val minNumberForArchive = 30

  def run(): Unit = {
    val files = os.list(logPath)

    val forArchive = files.dropRight(5)

    if (forArchive.size >= minNumberForArchive) {
      Try {
        logger.info("Logs ready for archiving, starting...")

        val archiveName = s"archived_logs_${currentTime()}"
        val archivePath = logPath / archiveName

        logger.info(s"Logs will be archived to $archivePath")

        os.makeDir.all(archivePath)

        logger.info("Log archived folder created")

        logger.info("Moving each file to archive folder")
        forArchive.foreach(path => os.move.into(path, archivePath))
        logger.info("Files moved successfully")

        val zipLocation = logPath / s"$archiveName.zip"
        logger.info(s"Started zipping at location $zipLocation")
        os.zip(
          dest = zipLocation,
          sources = Seq(archivePath)
        )
        logger.info("Logs zipped")

        logger.info(s"Removing $archivePath")
        os.remove.all(archivePath)
        logger.info(s"$archivePath removed")
      }.fold(
        error => logger.warn("Error compacting logs, continuing...", error),
        _ => logger.info("Logs archived successfully")
      )
    } else {
      logger.info(s"Not enough files for archive ${forArchive.size}, minimal need $minNumberForArchive")
    }

  }

  private def currentTime(): String = {
    val date = LocalDate.now()
    formatter.format(date)
  }

}
