package com.togger.logentry

import cats.implicits._
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PaginationTestSupport, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.temporal.ChronoUnit
import java.util.UUID

/** Log entries of a task are listed newest first; their versions newest version first. */
class LogEntryRepoPaginationTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TgTaskStatus
    with TimeHelpers
    with PaginationTestSupport {

  private val taskRepo = new TaskRepoImpl
  private val logEntryRepo = new LogEntryRepoImpl
  private val logEntryVersionRepo = new LogEntryVersionRepoImpl

  private case class Seeded(accountId: UUID, task: Task, otherTask: Task)

  private def seed(): ConnectionIO[Seeded] =
    for {
      account <- createAccount
      orgAndProject <- createOrgAndProject
      projectId = orgAndProject._2.id
      task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant))
      otherTask <- taskRepo.insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant))
    } yield Seeded(account.id, task, otherTask)

  private def newEntry(taskId: UUID, accountId: UUID, minutesAgo: Long) =
    LogEntry(UUID.randomUUID(), taskId, accountId, nowAsInstant.minus(minutesAgo, ChronoUnit.MINUTES))

  private def newVersion(logEntryId: UUID, accountId: UUID, index: Int) =
    LogEntryVersion(
      version = UUID.randomUUID(),
      logEntryId = logEntryId,
      textInternal = s"Internal $index",
      textExternal = s"External $index",
      status = LogEntryStatus.InProgress,
      createdBy = accountId,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

  private def readEntries(taskId: UUID)(page: DbPageRequest) = logEntryRepo.findPaginatedByTaskId(taskId, page)

  private def readVersions(logEntryId: UUID)(page: DbPageRequest) =
    logEntryVersionRepo.findPaginatedByLogEntryId(logEntryId, page)

  testNameFromClassName should {

    "split 11 log entries of a task over 3 pages of 5, newest first, and give an empty page past the end" in {
      transactor =>
        (for {
          seeded <- seed()
          // entry i was created i minutes ago, inserted oldest first, so the newest is entry 0
          entries <- (0 to 10).toList.reverse.traverse(minutes =>
            logEntryRepo.insert(newEntry(seeded.task.id, seeded.accountId, minutes.toLong))
          )
          _ <- logEntryRepo.insert(newEntry(seeded.otherTask.id, seeded.accountId, 0L))
          result <- assertElevenRowsOverThreePages(readEntries(seeded.task.id), entries.reverse.map(_.id))(_.id)
        } yield result).transact(transactor)
    }

    "only see the log entries of the task" in { transactor =>
      (for {
        seeded <- seed()
        mine <- logEntryRepo.insert(newEntry(seeded.task.id, seeded.accountId, 0L))
        _ <- logEntryRepo.insert(newEntry(seeded.otherTask.id, seeded.accountId, 0L))
        found <- logEntryRepo.findPaginatedByTaskId(seeded.task.id, pageRequest(1, 100))
        unknown <- logEntryRepo.findPaginatedByTaskId(UUID.randomUUID(), pageRequest(1, 100))
      } yield {
        found.items shouldBe List(mine)
        unknown.items shouldBe empty
        unknown.totalNumberOfItems shouldBe 0L
      }).transact(transactor)
    }

    "split 11 versions of a log entry over 3 pages of 5, newest version first" in { transactor =>
      (for {
        seeded <- seed()
        entry <- logEntryRepo.insert(newEntry(seeded.task.id, seeded.accountId, 0L))
        otherEntry <- logEntryRepo.insert(newEntry(seeded.otherTask.id, seeded.accountId, 0L))
        versions <- (0 to 10).toList.traverse(index =>
          logEntryVersionRepo.insert(newVersion(entry.id, seeded.accountId, index))
        )
        _ <- logEntryVersionRepo.insert(newVersion(otherEntry.id, seeded.accountId, 99))
        result <- assertElevenRowsOverThreePages(
          readVersions(entry.id),
          versions.map(_.version).sortBy(_.toString).reverse
        )(_.version)
      } yield result).transact(transactor)
    }
  }
}
