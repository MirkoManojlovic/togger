package com.togger.task

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{DbPageRequest, PaginatedResponse, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.suites.TestNameHelper
import com.togger.task.TaskError.{CannotDeleteLastVersion, CannotEditLatestVersion}
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.LocalDate
import java.util.UUID

class TaskVersionServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val taskVersionRepo: TaskVersionRepo = mock[TaskVersionRepo]

  val service = new TaskVersionService(taskVersionRepo, transactorMock)

  val taskId: UUID = UUID.randomUUID()
  val taskStatusId: UUID = UUID.randomUUID()
  val testStartDate: LocalDate = LocalDate.now()
  val testDueDate: LocalDate = testStartDate.plusDays(7)

  def newVersion(suffix: String) = TaskVersion(
    version = UUID.randomUUID(),
    taskId = taskId,
    name = s"name $suffix",
    description = s"description $suffix",
    assignedTo = None,
    closedBy = None,
    taskStatusId = taskStatusId,
    startDate = testStartDate,
    dueDate = testDueDate,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "list versions for a task" in {
      val version = newVersion("v1")

      (taskVersionRepo.findPaginatedByTaskId _)
        .expects(taskId, DbPageRequest.default)
        .returns(PaginatedResponse.of(1, 20, List(version), 1L).pure[ConnectionIO])

      service
        .listVersions(taskId, DbPageRequest.default)
        .map(_.items shouldBe List(version))
        .unsafeToFuture()
    }

    "reject updating the latest version in place, but allow updating an older one" in {
      val firstVersion = newVersion("v1")
      val secondVersion = newVersion("v2")
      val editedFirstVersion =
        firstVersion.withNewContent(
          "old name edit",
          "old description edit",
          None,
          None,
          taskStatusId,
          TaskPriority.Medium,
          startDate = testStartDate,
          dueDate = testDueDate
        )

      (taskVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(taskId).returns(secondVersion.pure[ConnectionIO])
      (taskVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(taskId).returns(secondVersion.pure[ConnectionIO])
      (taskVersionRepo.updateContent _)
        .expects(editedFirstVersion)
        .returns(editedFirstVersion.pure[ConnectionIO])

      (for {
        editLatestResult <- service.updateVersionContent(
          secondVersion.version,
          "edited name",
          "edited description",
          None,
          None,
          taskStatusId,
          TaskPriority.Medium,
          startDate = testStartDate,
          dueDate = testDueDate
        )
        editOldResult <- service.updateVersionContent(
          firstVersion.version,
          "old name edit",
          "old description edit",
          None,
          None,
          taskStatusId,
          TaskPriority.Medium,
          startDate = testStartDate,
          dueDate = testDueDate
        )
      } yield {
        val cp = new Checkpoint
        cp(editLatestResult shouldBe Left(CannotEditLatestVersion))
        cp.reportAll()
        editOldResult.map(_.description) shouldBe Right("old description edit")
      }).unsafeToFuture()
    }

    "auto-promote the previous version when the latest is deleted, and reject deleting the last one" in {
      val firstVersion = newVersion("v1")
      val secondVersion = newVersion("v2")

      (taskVersionRepo.findByVersion _).expects(secondVersion.version).returns(secondVersion.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(taskId).returns(secondVersion.pure[ConnectionIO])
      (taskVersionRepo.countByTaskId _).expects(taskId).returns(2.pure[ConnectionIO])
      (taskVersionRepo.deleteByVersion _).expects(secondVersion.version).returns(1.pure[ConnectionIO])

      (taskVersionRepo.findByVersion _).expects(firstVersion.version).returns(firstVersion.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(taskId).returns(firstVersion.pure[ConnectionIO])
      (taskVersionRepo.countByTaskId _).expects(taskId).returns(1.pure[ConnectionIO])

      (for {
        deleteLatestResult <- service.deleteVersion(secondVersion.version)
        deleteLastResult <- service.deleteVersion(firstVersion.version)
      } yield {
        val cp = new Checkpoint
        cp(deleteLatestResult shouldBe Right(1))
        cp.reportAll()
        deleteLastResult shouldBe Left(CannotDeleteLastVersion)
      }).unsafeToFuture()
    }

  }

}
