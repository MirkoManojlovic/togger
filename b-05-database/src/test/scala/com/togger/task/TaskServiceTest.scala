package com.togger.task

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{TgClock, TgIds, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

class TaskServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with TestNameHelper
    with MockedTransactor
    with TimeHelpers
    with Checkpoints
    with TestLoggingJvm {

  val taskRepo: TaskRepo = mock[TaskRepo]
  val linkRepo: LinkRepo = mock[LinkRepo]
  val linkVersionRepo: LinkVersionRepo = mock[LinkVersionRepo]
  val logEntryRepo: LogEntryRepo = mock[LogEntryRepo]
  val logEntryVersionRepo: LogEntryVersionRepo = mock[LogEntryVersionRepo]

  val tgClock = new TgClock {
    override def currentInstant: Instant = nowAsInstant

    override def currentOffsetDateTime(zoneOffset: ZoneOffset): OffsetDateTime = ???

    override def currentYear(zoneOffset: ZoneOffset): Int = ???

    override def currentDate(zoneOffset: ZoneOffset): LocalDate = ???
  }

  val taskService =
    new TaskService(taskRepo, linkRepo, linkVersionRepo, logEntryRepo, logEntryVersionRepo, tgClock, transactorMock)

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    name = "Test Task",
    description = "A task for testing",
    assignedTo = None,
    createdBy = TgIds.UNASSIGNED_UUID,
    taskStatusId = UUID.randomUUID(),
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val link1 = Link(id = UUID.randomUUID(), taskId = task.id, createdAt = nowAsInstant)
  val link2 = Link(id = UUID.randomUUID(), taskId = task.id, createdAt = nowAsInstant)

  val version1 = LinkVersion(
    version = UUID.randomUUID(),
    linkId = link1.id,
    name = "link 1",
    url = "http://example.com/1",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val version2 = LinkVersion(
    version = UUID.randomUUID(),
    linkId = link2.id,
    name = "link 2",
    url = "http://example.com/2",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {
    "insert the task and its links, each with a first version" in {
      (taskRepo.insert _).expects(task).returns(task.pure[ConnectionIO])
      (linkRepo.insert _).expects(link1).returns(link1.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(version1).returns(version1.pure[ConnectionIO])
      (linkRepo.insert _).expects(link2).returns(link2.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(version2).returns(version2.pure[ConnectionIO])

      val resultIO: IO[(Task, List[LinkWithOneVersion])] =
        taskService.createTask(
          task,
          Seq(
            LinkWithOneVersion(link1, version1),
            LinkWithOneVersion(link2, version2)
          )
        )

      resultIO
        .map { case (returnedTask, returnedLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe task)
          cp.reportAll()
          returnedLinks should contain theSameElementsAs List(
            LinkWithOneVersion(link1, version1),
            LinkWithOneVersion(link2, version2)
          )
        }
        .unsafeToFuture()
    }

    "save a new version for an updated link instead of overwriting it" in {
      val updatedVersion = version1.withNewContent("new name", "http://example.com/new")

      (taskRepo.update _).expects(task, nowAsInstant).returns(task.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(updatedVersion).returns(updatedVersion.pure[ConnectionIO])
      (linkRepo.findById _).expects(link1.id).returns(link1.pure[ConnectionIO])

      val resultIO: IO[(Task, List[LinkWithOneVersion], List[LinkWithOneVersion])] =
        taskService.updateTask(task, Seq(updatedVersion), Seq.empty)

      resultIO
        .map { case (returnedTask, updatedLinks, createdLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe task)
          cp(updatedLinks shouldBe List(LinkWithOneVersion(link1, updatedVersion)))
          cp.reportAll()
          createdLinks shouldBe empty
        }
        .unsafeToFuture()
    }
  }

}
