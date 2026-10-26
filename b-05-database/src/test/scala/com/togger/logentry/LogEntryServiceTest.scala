package com.togger.logentry

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{DbPageRequest, PaginatedResponse, TgIds, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.logentry.LogEntryError.{CannotDeleteLastVersion, CannotEditLatestVersion, LogEntryAlreadyExistsForDay}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.temporal.ChronoUnit
import java.util.UUID

class LogEntryServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val logEntryRepo: LogEntryRepo = mock[LogEntryRepo]
  val logEntryVersionRepo: LogEntryVersionRepo = mock[LogEntryVersionRepo]

  val service = new LogEntryService(logEntryRepo, logEntryVersionRepo, transactorMock)

  val taskId: UUID = UUID.randomUUID()
  val accountId: UUID = TgIds.UNASSIGNED_UUID

  def newLogEntry() = LogEntry(
    id = UUID.randomUUID(),
    taskId = taskId,
    createdBy = accountId,
    createdAt = nowAsInstant
  )

  def newVersion(suffix: String) = LogEntryVersion(
    version = UUID.randomUUID(),
    logEntryId = UUID.randomUUID(),
    textInternal = s"internal $suffix",
    textExternal = s"external $suffix",
    status = LogEntryStatus.InProgress,
    createdBy = accountId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "create a log entry with a single version" in {
      val logEntry = newLogEntry()
      val version = newVersion("v1").copy(logEntryId = logEntry.id)

      (logEntryRepo.insert _).expects(logEntry).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(version).returns(version.pure[ConnectionIO])
      (logEntryVersionRepo.findPaginatedByLogEntryId _)
        .expects(logEntry.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(version), 1L).pure[ConnectionIO])

      (for {
        result <- service.createLogEntry(logEntry, version)
        savedEntry = result.logEntry
        savedVersion = result.version
        versions <- service.listVersions(savedEntry.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(versions shouldBe List(savedVersion))
        cp.reportAll()
        savedVersion.logEntryId shouldBe savedEntry.id
      }).unsafeToFuture()
    }

    "create a log entry when the task has none for that day" in {
      val logEntry = newLogEntry()
      val version = newVersion("v1").copy(logEntryId = logEntry.id)
      val dayStart = logEntry.createdAt.truncatedTo(ChronoUnit.DAYS)

      (logEntryRepo.existsByTaskIdCreatedBetween _)
        .expects(taskId, dayStart, dayStart.plus(1, ChronoUnit.DAYS))
        .returns(false.pure[ConnectionIO])
      (logEntryRepo.insert _).expects(logEntry).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(version).returns(version.pure[ConnectionIO])

      service
        .createLogEntryOncePerDay(logEntry, version)
        .map(_ shouldBe Right(LogEntryWithOneVersion(logEntry, version)))
        .unsafeToFuture()
    }

    "reject a second log entry for the same task on the same day" in {
      val logEntry = newLogEntry()
      val version = newVersion("v1").copy(logEntryId = logEntry.id)
      val dayStart = logEntry.createdAt.truncatedTo(ChronoUnit.DAYS)

      (logEntryRepo.existsByTaskIdCreatedBetween _)
        .expects(taskId, dayStart, dayStart.plus(1, ChronoUnit.DAYS))
        .returns(true.pure[ConnectionIO])

      service
        .createLogEntryOncePerDay(logEntry, version)
        .map(_ shouldBe Left(LogEntryAlreadyExistsForDay))
        .unsafeToFuture()
    }

    "save a new version on every save, keeping old ones and moving the latest" in {
      val logEntry = newLogEntry()
      val firstVersion = newVersion("v1").copy(logEntryId = logEntry.id)
      val secondVersion = newVersion("v2").copy(logEntryId = logEntry.id)

      (logEntryRepo.insert _).expects(logEntry).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (logEntryRepo.findById _).expects(logEntry.id).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (logEntryRepo.findPaginatedByTaskId _)
        .expects(taskId, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(logEntry), 1L).pure[ConnectionIO])
      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findPaginatedByLogEntryId _)
        .expects(logEntry.id, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(secondVersion, firstVersion), 2L).pure[ConnectionIO])

      (for {
        created <- service.createLogEntry(logEntry, firstVersion)
        savedEntry = created.logEntry
        secondResult <- service.saveNewVersion(savedEntry.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        current <- service.findPaginatedByTaskId(taskId, DbPageRequest.default).map(_.items)
        allVersions <- service.listVersions(savedEntry.id, DbPageRequest.default).map(_.items)
      } yield {
        val cp = new Checkpoint
        cp(allVersions.map(_.version) shouldBe List(savedSecondVersion.version, firstVersion.version))
        cp(current.map(_.versions.head.version) shouldBe List(savedSecondVersion.version))
        cp.reportAll()
        current.map(_.logEntry.id) shouldBe List(savedEntry.id)
      }).unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val logEntry = newLogEntry()
      val firstVersion = newVersion("v1").copy(logEntryId = logEntry.id)
      val secondVersion = newVersion("v2").copy(logEntryId = logEntry.id)
      val editedFirstVersion =
        firstVersion.withNewContent("old internal edit", "old external edit", LogEntryStatus.Completed)

      (logEntryRepo.insert _).expects(logEntry).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (logEntryRepo.findById _).expects(logEntry.id).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.updateContent _).expects(editedFirstVersion).returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        created <- service.createLogEntry(logEntry, firstVersion)
        savedEntry = created.logEntry
        secondResult <- service.saveNewVersion(savedEntry.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        editLatestResult <- service.updateVersionContent(
          savedSecondVersion.version,
          "edited internal",
          "edited external",
          LogEntryStatus.Completed
        )
        editOldResult <- service.updateVersionContent(
          firstVersion.version,
          "old internal edit",
          "old external edit",
          LogEntryStatus.Completed
        )
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.textExternal) shouldBe Right("old external edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val logEntry = newLogEntry()
      val firstVersion = newVersion("v1").copy(logEntryId = logEntry.id)
      val secondVersion = newVersion("v2").copy(logEntryId = logEntry.id)

      (logEntryRepo.insert _).expects(logEntry).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (logEntryRepo.findById _).expects(logEntry.id).returns(logEntry.pure[ConnectionIO])
      (logEntryVersionRepo.insert _).expects(secondVersion).returns(secondVersion.pure[ConnectionIO])

      (logEntryVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(secondVersion.pure[ConnectionIO])
      (logEntryVersionRepo.countByLogEntryId _).expects(logEntry.id).returns(2.pure[ConnectionIO])
      (logEntryVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(firstVersion.pure[ConnectionIO])

      (logEntryVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (logEntryVersionRepo.findLatestByLogEntryId _).expects(logEntry.id).returns(firstVersion.pure[ConnectionIO])
      (logEntryVersionRepo.countByLogEntryId _).expects(logEntry.id).returns(1.pure[ConnectionIO])

      (for {
        created <- service.createLogEntry(logEntry, firstVersion)
        savedEntry = created.logEntry
        secondResult <- service.saveNewVersion(savedEntry.id, secondVersion)
        savedSecondVersion = secondResult.toOption.get.version
        deleteLatestResult <- service.deleteVersion(savedSecondVersion.version)
        latestAfterDelete <- logEntryVersionRepo.findLatestByLogEntryId(savedEntry.id).transact(transactorMock)
        deleteLastResult <- service.deleteVersion(firstVersion.version)
      } yield {
        val cp = new Checkpoint
        cp(deleteLatestResult shouldBe Right(1))
        cp(latestAfterDelete shouldBe firstVersion)
        cp.reportAll()
        deleteLastResult shouldBe Left(CannotDeleteLastVersion)
      }).unsafeToFuture()
    }

  }

}
