package com.togger.task

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.LocalDate
import java.util.UUID

/** The version history of a task is listed newest version first (the versions are uuid7, so by time). */
class TaskHistoryRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TgTaskStatus
    with TimeHelpers
    with PaginationTestSupport {

  private val taskRepo = new TaskRepoImpl
  private val taskVersionRepo = new TaskVersionRepoImpl

  private case class Seeded(task: Task, otherTask: Task, statusId: UUID, versions: List[TaskVersion])

  private def newVersion(taskId: UUID, statusId: UUID, index: Int) =
    TaskVersion(
      version = UUID.randomUUID(),
      taskId = taskId,
      name = s"Task $index",
      description = s"Description $index",
      assignedTo = None,
      closedBy = None,
      taskStatusId = statusId,
      priority = TaskPriority.Medium,
      startDate = LocalDate.of(2026, 1, 5),
      dueDate = LocalDate.of(2026, 1, 12),
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private def seed(): ConnectionIO[Seeded] =
    for {
      account <- createAccount
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      status <- createTaskStatus(projectId)
      task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant))
      otherTask <- taskRepo.insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant))
      versions <- (0 to 10).toList.traverse(index => taskVersionRepo.insert(newVersion(task.id, status.id, index)))
      _ <- taskVersionRepo.insert(newVersion(otherTask.id, status.id, 99))
    } yield Seeded(task, otherTask, status.id, versions)

  private def readPages(taskId: UUID)(page: DbPageRequest) = taskVersionRepo.findPaginatedByTaskId(taskId, page)

  testNameFromClassName should {

    "split 11 versions over 3 pages of 5, newest version first, and give an empty page past the end" in { transactor =>
      (for {
        seeded <- seed()
        result <- assertElevenRowsOverThreePages(
          readPages(seeded.task.id),
          seeded.versions.map(_.version).sortBy(_.toString).reverse
        )(_.version)
      } yield result).transact(transactor)
    }

    "only see the versions of the task, in the order of the unbounded list" in { transactor =>
      (for {
        seeded <- seed()
        paged <- taskVersionRepo.findPaginatedByTaskId(seeded.task.id, pageRequest(1, 100))
        all <- taskVersionRepo.findByTaskId(seeded.task.id)
        unknown <- taskVersionRepo.findPaginatedByTaskId(UUID.randomUUID(), pageRequest(1, 100))
      } yield {
        paged.items shouldBe all
        paged.totalNumberOfItems shouldBe 11L
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }
  }
}
