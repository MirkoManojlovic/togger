package com.togger.logentry

import cats.effect.IO
import cats.implicits._
import com.togger.logentry.LogEntryError.{CannotDeleteLastVersion, CannotEditLatestVersion, LogEntryError, LogEntryNotFound, VersionNotFound}
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class LogEntryService(
  logEntryRepo: LogEntryRepo,
  logEntryVersionRepo: LogEntryVersionRepo,
  transactor: Transactor[IO]
) extends TgLogging {

  def createLogEntry(logEntry: LogEntry, firstVersion: LogEntryVersion): IO[LogEntryWithOneVersion] = {
    logger.info(s"Creating a ${sp(logEntry)}, with ${sp(firstVersion)}")
    (for {
      savedEntry <- logEntryRepo.insert(logEntry)
      savedVersion <- logEntryVersionRepo.insert(firstVersion.copy(logEntryId = savedEntry.id))
    } yield {
      logger.info(s"Log entry created successfully ${sp(savedEntry)} with ${sp(savedVersion)}")
      LogEntryWithOneVersion(savedEntry, savedVersion)
    }).transact(transactor)
  }

  def saveNewVersion(
    logEntryId: UUID,
    newVersion: LogEntryVersion
  ): IO[Either[LogEntryError, LogEntryWithOneVersion]] =
    (for {
      entry <- logEntryRepo.findById(logEntryId)
      savedVersion <- logEntryVersionRepo.insert(newVersion.copy(logEntryId = logEntryId))
    } yield LogEntryWithOneVersion(entry, savedVersion))
      .transact(transactor)
      .map(Right(_))
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(LogEntryNotFound))
      }

  def findByTaskId(taskId: UUID): IO[List[LogEntryWithVersions]] =
    (for {
      entries <- logEntryRepo.findByTaskId(taskId)
      combined <- entries.traverse(logEntry =>
        logEntryVersionRepo
          .findLatestByLogEntryId(logEntry.id)
          .map(logEntryVersion => LogEntryWithVersions(logEntry, Seq(logEntryVersion)))
      )
    } yield combined).transact(transactor)

  def listVersions(logEntryId: UUID): IO[List[LogEntryVersion]] =
    logEntryVersionRepo.findByLogEntryId(logEntryId).transact(transactor)

  def updateVersionContent(
    version: UUID,
    textInternal: String,
    textExternal: String,
    status: LogEntryStatus
  ): IO[Either[LogEntryError, LogEntryVersion]] =
    (for {
      existingVersion <- logEntryVersionRepo.findByVersion(version)
      latestVersion <- logEntryVersionRepo.findLatestByLogEntryId(existingVersion.logEntryId)
      result <-
        if (existingVersion.version == latestVersion.version)
          (CannotEditLatestVersion: LogEntryError).asLeft[LogEntryVersion].pure[doobie.ConnectionIO]
        else
          logEntryVersionRepo
            .updateContent(existingVersion.withNewContent(textInternal, textExternal, status))
            .map(_.asRight[LogEntryError])
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

  def deleteVersion(version: UUID): IO[Either[LogEntryError, Int]] =
    (for {
      existingVersion <- logEntryVersionRepo.findByVersion(version)
      latestVersion <- logEntryVersionRepo.findLatestByLogEntryId(existingVersion.logEntryId)
      result <-
        if (existingVersion.version != latestVersion.version)
          logEntryVersionRepo.deleteByVersion(version).map(_.asRight[LogEntryError])
        else
          logEntryVersionRepo
            .countByLogEntryId(existingVersion.logEntryId)
            .flatMap(count =>
              if (count <= 1)
                (CannotDeleteLastVersion: LogEntryError).asLeft[Int].pure[doobie.ConnectionIO]
              else
                logEntryVersionRepo.deleteByVersion(version).map(_.asRight[LogEntryError])
            )
    } yield result)
      .transact(transactor)
      .recoverWith { case UnexpectedEnd =>
        IO.pure(Left(VersionNotFound))
      }

}
