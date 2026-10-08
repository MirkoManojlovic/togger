package com.togger.task

import cats.effect.IO
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TimeHelpers}
import com.togger.dto.TaskFilter
import com.togger.logentry.{LogEntryRepoImpl, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.Transactor
import doobie.implicits._

import java.time.LocalDate
import java.util.UUID

/** Against the real database, since what matters here is that the batch is one transaction. */
class TaskServiceBulkTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  private val taskVersionRepo = new TaskVersionRepoImpl

  private def taskService(transactor: Transactor[IO]) =
    new TaskService(
      new TaskRepoImpl,
      taskVersionRepo,
      new LinkRepoImpl,
      new LinkVersionRepoImpl,
      new LogEntryRepoImpl,
      new LogEntryVersionRepoImpl,
      new TaskLabelAssignmentRepoImpl,
      transactor
    )

  private val startDate = LocalDate.of(2026, 10, 12)

  private def newTask(name: String, projectId: UUID, createdBy: UUID, statusId: UUID, index: Int): NewTask = {
    val createdAt = nowAsInstant.plusMillis(index.toLong)
    val task = Task(UUID.randomUUID(), projectId, createdBy, createdAt)
    NewTask(
      task,
      TaskVersion(
        version = UUID.randomUUID(),
        taskId = task.id,
        name = name,
        description = "",
        assignedTo = None,
        closedBy = None,
        taskStatusId = statusId,
        startDate = startDate,
        dueDate = startDate.plusDays(4),
        createdAt = createdAt,
        updatedAt = createdAt
      ),
      newLinks = Seq.empty
    )
  }

  testNameFromClassName should {

    "create all tasks, in list order" in { transactor =>
      for {
        fixtures <- (for {
          account <- createAccount
          orgAndProject <- createOrgAndProject
          status <- createTaskStatus(orgAndProject._2.id)
        } yield (account.id, orgAndProject._2.id, status.id)).transact(transactor)
        (accountId, projectId, statusId) = fixtures
        created <- taskService(transactor).createTasks(
          Seq("A", "B", "C").zipWithIndex.map { case (name, index) =>
            newTask(name, projectId, accountId, statusId, index)
          }
        )
        stored <- taskVersionRepo
          .findPaginatedByProjectId(projectId, TaskFilter.empty, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        created.map(_._2.map(_.link)) shouldBe List(Nil, Nil, Nil)
        created.map(_._1.version.name) shouldBe List("A", "B", "C")
        stored.sortBy(_.createdAt).map(_.name) shouldBe List("A", "B", "C")
      }
    }

    "create none of the tasks when one of them fails" in { transactor =>
      for {
        fixtures <- (for {
          account <- createAccount
          orgAndProject <- createOrgAndProject
          status <- createTaskStatus(orgAndProject._2.id)
        } yield (account.id, orgAndProject._2.id, status.id)).transact(transactor)
        (accountId, projectId, statusId) = fixtures
        result <- taskService(transactor)
          .createTasks(
            Seq(
              newTask("A", projectId, accountId, statusId, 0),
              newTask("B", projectId, accountId, UUID.randomUUID(), 1) // a status that does not exist
            )
          )
          .attempt
        stored <- taskVersionRepo
          .findPaginatedByProjectId(projectId, TaskFilter.empty, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        result.isLeft shouldBe true
        stored shouldBe empty
      }
    }
  }
}
