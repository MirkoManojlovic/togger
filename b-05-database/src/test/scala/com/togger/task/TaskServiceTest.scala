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
  val logEntryRepo: LogEntryRepo = mock[LogEntryRepo]
  val logEntryVersionRepo: LogEntryVersionRepo = mock[LogEntryVersionRepo]

  val tgClock = new TgClock {
    override def currentInstant: Instant = nowAsInstant

    override def currentOffsetDateTime(zoneOffset: ZoneOffset): OffsetDateTime = ???

    override def currentYear(zoneOffset: ZoneOffset): Int = ???

    override def currentDate(zoneOffset: ZoneOffset): LocalDate = ???
  }

  val taskService = new TaskService(taskRepo, linkRepo, logEntryRepo, logEntryVersionRepo, tgClock, transactorMock)

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

  val links = List(
    Link(
      id = UUID.randomUUID(),
      taskId = task.id,
      url = "http://example.com/1",
      name = "link 1",
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    ),
    Link(
      id = UUID.randomUUID(),
      taskId = task.id,
      url = "http://example.com/2",
      name = "link 2",
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )
  )

  testNameFromClassName should {
    "insert the task and its links, returning the saved task and links" in {
      (taskRepo.insert _).expects(task).returns(task.pure[ConnectionIO])
      (linkRepo.insertMany _).expects(links).returns(links.pure[ConnectionIO])

      val resultIO: IO[(Task, List[Link])] = taskService.createTask(task, links)

      resultIO
        .map { case (returnedTask, returnedLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe task)
          cp.reportAll()
          returnedLinks should contain theSameElementsAs links
        }
        .unsafeToFuture()
    }
  }

}
