package com.togger.logentry

import cats.effect.IO
import cats.implicits._
import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import com.togger.dto.LogEntryFilter
import com.togger.logentry.LogEntryError.CannotDeleteLastVersion
import com.togger.logentry.LogEntryError.CannotEditLatestVersion
import com.togger.logentry.LogEntryError.LogEntryAlreadyExistsForDay
import com.togger.logentry.LogEntryError.LogEntryError
import com.togger.logentry.LogEntryError.LogEntryNotFound
import com.togger.logentry.LogEntryError.VersionNotFound
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

import java.time.temporal.ChronoUnit
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

  /** Like [[createLogEntry]], but rejects a second log entry for the same task on the same (UTC) day. */
  def createLogEntryOncePerDay(
    logEntry: LogEntry,
    firstVersion: LogEntryVersion
  ): IO[Either[LogEntryError, LogEntryWithOneVersion]] = {
    val dayStart = logEntry.createdAt.truncatedTo(ChronoUnit.DAYS)
    val dayEnd = dayStart.plus(1, ChronoUnit.DAYS)
    logEntryRepo
      .existsByTaskIdCreatedBetween(logEntry.taskId, dayStart, dayEnd)
      .transact(transactor)
      .flatMap { exists =>
        if (exists) {
          logger.warn(s"Rejecting ${sp(logEntry)}: the task already has a log entry for that day")
          IO.pure(Left(LogEntryAlreadyExistsForDay))
        } else createLogEntry(logEntry, firstVersion).map(Right(_))
      }
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

  def findPaginatedByTaskId(taskId: UUID, page: DbPageRequest): IO[PaginatedResponse[LogEntryWithVersions]] =
    (for {
      entriesPage <- logEntryRepo.findPaginatedByTaskId(taskId, page)
      combined <- entriesPage.items.traverse(logEntry =>
        logEntryVersionRepo
          .findLatestByLogEntryId(logEntry.id)
          .map(logEntryVersion => LogEntryWithVersions(logEntry, Seq(logEntryVersion)))
      )
    } yield entriesPage.copy(items = combined)).transact(transactor)

  def findPaginatedByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: DbPageRequest
  ): IO[PaginatedResponse[LogEntryWithVersions]] =
    (for {
      entriesPage <- logEntryRepo.findPaginatedByProjectId(projectId, filter, page)
      combined <- entriesPage.items.traverse(logEntry =>
        logEntryVersionRepo
          .findLatestByLogEntryId(logEntry.id)
          .map(logEntryVersion => LogEntryWithVersions(logEntry, Seq(logEntryVersion)))
      )
    } yield entriesPage.copy(items = combined)).transact(transactor)

  def listVersions(logEntryId: UUID, page: DbPageRequest): IO[PaginatedResponse[LogEntryVersion]] =
    logEntryVersionRepo.findPaginatedByLogEntryId(logEntryId, page).transact(transactor)

  def updateVersionContent(
    version: UUID,
    text: String,
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
            .updateContent(existingVersion.withNewContent(text, status))
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

  def deleteLogEntry(id: UUID): IO[Either[LogEntryError, Int]] =
    logEntryRepo
      .deleteById(id)
      .map(deleted => Either.cond(deleted > 0, deleted, LogEntryNotFound: LogEntryError))
      .transact(transactor)

}
