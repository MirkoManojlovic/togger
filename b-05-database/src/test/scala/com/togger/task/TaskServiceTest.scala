package com.togger.task

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common.{TgIds, TimeHelpers}
import com.togger.db.MockedTransactor
import com.togger.logentry.{LogEntryRepo, LogEntryVersionRepo}
import com.togger.suites.TestNameHelper
import doobie.ConnectionIO
import doobie.implicits._
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.Checkpoints
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

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
  val taskVersionRepo: TaskVersionRepo = mock[TaskVersionRepo]
  val linkRepo: LinkRepo = mock[LinkRepo]
  val linkVersionRepo: LinkVersionRepo = mock[LinkVersionRepo]
  val logEntryRepo: LogEntryRepo = mock[LogEntryRepo]
  val logEntryVersionRepo: LogEntryVersionRepo = mock[LogEntryVersionRepo]
  val taskLabelAssignmentRepo: TaskLabelAssignmentRepo = mock[TaskLabelAssignmentRepo]

  val taskService =
    new TaskService(
      taskRepo,
      taskVersionRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      taskLabelAssignmentRepo,
      transactorMock
    )

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  val firstVersion = TaskVersion(
    version = UUID.randomUUID(),
    taskId = task.id,
    name = "Test Task",
    description = "A task for testing",
    assignedTo = None,
    closedBy = None,
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
    "insert the task with a first version, and its links, each with a first version" in {
      (taskRepo.insert _).expects(task).returns(task.pure[ConnectionIO])
      (taskVersionRepo.insert _).expects(firstVersion).returns(firstVersion.pure[ConnectionIO])
      (linkRepo.insert _).expects(link1).returns(link1.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(version1).returns(version1.pure[ConnectionIO])
      (linkRepo.insert _).expects(link2).returns(link2.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(version2).returns(version2.pure[ConnectionIO])

      val resultIO: IO[(TaskWithOneVersion, List[LinkWithOneVersion])] =
        taskService.createTask(
          task,
          firstVersion,
          Seq(
            LinkWithOneVersion(link1, version1),
            LinkWithOneVersion(link2, version2)
          )
        )

      resultIO
        .map { case (returnedTask, returnedLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe TaskWithOneVersion(task, firstVersion))
          cp.reportAll()
          returnedLinks should contain theSameElementsAs List(
            LinkWithOneVersion(link1, version1),
            LinkWithOneVersion(link2, version2)
          )
        }
        .unsafeToFuture()
    }

    "save a new task version instead of overwriting, and a new version for an updated link" in {
      val newTaskVersion =
        firstVersion.withNewContent("new name", "new description", None, None, UUID.randomUUID(), TaskPriority.Medium)
      val updatedLinkVersion = version1.withNewContent("new name", "http://example.com/new")

      (taskRepo.findById _).expects(task.id).returns(task.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(task.id).returns(firstVersion.pure[ConnectionIO])
      (taskVersionRepo.insert _).expects(newTaskVersion).returns(newTaskVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(link1.id).returns(version1.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(updatedLinkVersion).returns(updatedLinkVersion.pure[ConnectionIO])
      (linkRepo.findById _).expects(link1.id).returns(link1.pure[ConnectionIO])
      (taskLabelAssignmentRepo.deleteAllByTaskId _).expects(task.id).returns(0.pure[ConnectionIO])

      val resultIO: IO[(TaskWithOneVersion, List[LinkWithOneVersion], List[LinkWithOneVersion])] =
        taskService.updateTask(task.id, newTaskVersion, Seq(updatedLinkVersion), Seq.empty)

      resultIO
        .map { case (returnedTask, updatedLinks, createdLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe TaskWithOneVersion(task, newTaskVersion))
          cp(updatedLinks shouldBe List(LinkWithOneVersion(link1, updatedLinkVersion)))
          cp.reportAll()
          createdLinks shouldBe empty
        }
        .unsafeToFuture()
    }

    "not insert a new task version when only links change" in {
      val sameContentVersion =
        firstVersion.copy(version = UUID.randomUUID(), createdAt = nowAsInstant, updatedAt = nowAsInstant)
      val updatedLinkVersion = version1.withNewContent("new name", "http://example.com/new")

      (taskRepo.findById _).expects(task.id).returns(task.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(task.id).returns(firstVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(link1.id).returns(version1.pure[ConnectionIO])
      (linkVersionRepo.insert _).expects(updatedLinkVersion).returns(updatedLinkVersion.pure[ConnectionIO])
      (linkRepo.findById _).expects(link1.id).returns(link1.pure[ConnectionIO])
      (taskLabelAssignmentRepo.deleteAllByTaskId _).expects(task.id).returns(0.pure[ConnectionIO])

      val resultIO: IO[(TaskWithOneVersion, List[LinkWithOneVersion], List[LinkWithOneVersion])] =
        taskService.updateTask(task.id, sameContentVersion, Seq(updatedLinkVersion), Seq.empty)

      resultIO
        .map { case (returnedTask, updatedLinks, createdLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe TaskWithOneVersion(task, firstVersion))
          cp(updatedLinks shouldBe List(LinkWithOneVersion(link1, updatedLinkVersion)))
          cp.reportAll()
          createdLinks shouldBe empty
        }
        .unsafeToFuture()
    }

    "not insert a new link version when link content is unchanged" in {
      val sameContentLinkVersion =
        version1.copy(version = UUID.randomUUID(), createdAt = nowAsInstant, updatedAt = nowAsInstant)

      (taskRepo.findById _).expects(task.id).returns(task.pure[ConnectionIO])
      (taskVersionRepo.findLatestByTaskId _).expects(task.id).returns(firstVersion.pure[ConnectionIO])
      (linkVersionRepo.findLatestByLinkId _).expects(link1.id).returns(version1.pure[ConnectionIO])
      (linkRepo.findById _).expects(link1.id).returns(link1.pure[ConnectionIO])
      (taskLabelAssignmentRepo.deleteAllByTaskId _).expects(task.id).returns(0.pure[ConnectionIO])

      val resultIO: IO[(TaskWithOneVersion, List[LinkWithOneVersion], List[LinkWithOneVersion])] =
        taskService.updateTask(task.id, firstVersion, Seq(sameContentLinkVersion), Seq.empty)

      resultIO
        .map { case (returnedTask, updatedLinks, createdLinks) =>
          val cp = new Checkpoint
          cp(returnedTask shouldBe TaskWithOneVersion(task, firstVersion))
          cp(updatedLinks shouldBe List(LinkWithOneVersion(link1, version1)))
          cp.reportAll()
          createdLinks shouldBe empty
        }
        .unsafeToFuture()
    }

    "delete the task" in {
      (taskRepo.deleteById _).expects(task.id).returns(1.pure[ConnectionIO])

      taskService.deleteTask(task.id).map(_ shouldBe Right(1)).unsafeToFuture()
    }

    "return TaskNotFound when deleting a task that does not exist" in {
      (taskRepo.deleteById _).expects(task.id).returns(0.pure[ConnectionIO])

      taskService.deleteTask(task.id).map(_ shouldBe Left(TaskError.TaskNotFound)).unsafeToFuture()
    }
  }

}
