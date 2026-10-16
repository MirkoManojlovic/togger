package com.togger.timebox

import cats.effect.IO
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task._
import com.togger.timebox.TimeboxError.{TaskNotFound, TimeboxError, TimeboxNotFound}
import doobie.Transactor
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

class TimeboxServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val timeboxRepo = new TimeboxRepoImpl
  val timeboxVersionRepo = new TimeboxVersionRepoImpl

  val start: LocalDate = LocalDate.of(2026, 10, 12)
  val end: LocalDate = start.plusDays(6)

  def serviceFor(transactor: Transactor[IO]) =
    new TimeboxService(timeboxRepo, timeboxVersionRepo, taskRepo, taskVersionRepo, transactor)

  private def right[A](io: IO[Either[TimeboxError, A]]): IO[A] =
    io.flatMap(result => IO.fromEither(result.left.map(error => new RuntimeException(error.message))))

  def createTask(projectId: UUID, accountId: UUID, statusId: UUID, name: String) =
    for {
      task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, accountId, nowAsInstant))
      version <- taskVersionRepo.insert(
        TaskVersion(
          version = UUID.randomUUID(),
          taskId = task.id,
          name = name,
          description = "d",
          assignedTo = None,
          taskStatusId = statusId,
          startDate = start,
          dueDate = end,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
    } yield (task, version)

  testNameFromClassName should {

    "keep every version as a snapshot with the task versions planned at that time" in { transactor =>
      val service = serviceFor(transactor)
      for {
        fixtures <- (for {
          account <- createAccount
          orgAndProject <- createOrgAndProject
          status <- createTaskStatus(orgAndProject._2.id)
          t1 <- createTask(orgAndProject._2.id, account.id, status.id, "task 1")
          t2 <- createTask(orgAndProject._2.id, account.id, status.id, "task 2")
        } yield (orgAndProject._2, t1, t2)).transact(transactor)
        (project, (task1, task1v1), (task2, task2v1)) = fixtures
        created <- right(service.createTimebox(project.id, start, end, Some("first"), Seq(task1.id), nowAsInstant))
        // editing the task afterwards creates a new task version
        task1v2 <- taskVersionRepo
          .insert(task1v1.copy(version = UUID.randomUUID(), name = "task 1 edited"))
          .transact(transactor)
        second <- right(
          service.saveNewVersion(
            created.timebox.id,
            start.plusWeeks(1),
            end.plusWeeks(1),
            None,
            "second version",
            Seq(task1.id, task2.id),
            nowAsInstant
          )
        )
        versions <- service.listVersions(created.timebox.id, DbPageRequest.default).map(_.items)
        current <- right(service.getCurrent(created.timebox.id))
      } yield {
        versions.map(_.version.version) shouldBe List(second.version.version.version, created.version.version.version)
        current.version.version.version shouldBe second.version.version.version

        val oldVersion = versions.last
        oldVersion.version.description shouldBe Some("first")
        oldVersion.version.changeComment shouldBe ""
        oldVersion.version.startDate shouldBe start
        oldVersion.tasks.map(_.version) shouldBe List(task1v1.version)
        oldVersion.tasks.map(_.name) shouldBe List("task 1")

        val newVersion = versions.head
        newVersion.version.description shouldBe None
        newVersion.version.changeComment shouldBe "second version"
        newVersion.version.startDate shouldBe start.plusWeeks(1)
        newVersion.tasks.map(_.version).toSet shouldBe Set(task1v2.version, task2v1.version)
        newVersion.tasks.map(_.name).toSet shouldBe Set("task 1 edited", "task 2")
      }
    }

    "reject unknown tasks and unknown timeboxes" in { transactor =>
      val service = serviceFor(transactor)
      val unknownTask = UUID.randomUUID()
      for {
        project <- (for {
          _ <- createAccount
          orgAndProject <- createOrgAndProject
        } yield orgAndProject._2).transact(transactor)
        unknown <- service.createTimebox(project.id, start, end, None, Seq(unknownTask), nowAsInstant)
        noTimebox <- service.saveNewVersion(UUID.randomUUID(), start, end, None, "comment", Seq.empty, nowAsInstant)
        notFound <- service.getCurrent(UUID.randomUUID())
      } yield {
        unknown shouldBe Left(TaskNotFound(unknownTask))
        noTimebox shouldBe Left(TimeboxNotFound)
        notFound shouldBe Left(TimeboxNotFound)
      }
    }

    "delete the timebox with all of its versions" in { transactor =>
      val service = serviceFor(transactor)
      for {
        project <- (for {
          _ <- createAccount
          orgAndProject <- createOrgAndProject
        } yield orgAndProject._2).transact(transactor)
        created <- right(service.createTimebox(project.id, start, end, None, Seq.empty, nowAsInstant))
        _ <- right(service.saveNewVersion(created.timebox.id, start, end, None, "comment", Seq.empty, nowAsInstant))
        _ <- service.deleteTimebox(created.timebox.id)
        versions <- service.listVersions(created.timebox.id, DbPageRequest.default).map(_.items)
        found <- service.findPaginatedByProjectId(project.id, DbPageRequest.default)
      } yield {
        versions shouldBe empty
        found.items shouldBe empty
      }
    }
  }
}
