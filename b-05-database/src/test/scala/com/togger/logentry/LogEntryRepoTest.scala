package com.togger.logentry
import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.account.Account
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, TgIds, TimeHelpers}
import com.togger.dto.LogEntryFilter
import com.togger.project.Project
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class LogEntryRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val logEntryRepo = new LogEntryRepoImpl

  val task = Task(
    id = UUID.randomUUID(),
    projectId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  val logEntry = LogEntry(
    id = UUID.randomUUID(),
    taskId = UUID.randomUUID(),
    createdBy = TgIds.UNASSIGNED_UUID,
    createdAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedTask <- taskRepo.insert(task = task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
        savedLogEntry <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
        foundLogEntry1 <- logEntryRepo.findById(savedLogEntry.id)
        _ <- logEntryRepo.deleteById(foundLogEntry1.id)
        foundLogEntry2 <- logEntryRepo.findById(savedLogEntry.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedLogEntry shouldBe foundLogEntry1)
        cp.reportAll()
        foundLogEntry2.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
        logEntriesToInsert = createLogEntries(savedTask.id, savedAccount.id)
        savedLogEntries <- logEntriesToInsert.map(logEntry => logEntryRepo.insert(logEntry)).toList.sequence
        foundLogEntries <- logEntryRepo.findByTaskId(savedTask.id)
      } yield {
        foundLogEntries shouldBe savedLogEntries
      }

      res.transact(transactor)
    }

    def createLogEntries(taskId: UUID, accountId: UUID) = (0 to 10).map(index =>
      LogEntry(
        id = UUID.randomUUID(),
        taskId = taskId,
        createdBy = accountId,
        createdAt = nowAsInstant
      )
    )

    def pageOf(size: Int, page: Int) = DbPageRequest.fromPageRequest(PageRequest(page, size)).toOption.get

    def seedProjectEntries(savedProject1: Project, savedAccount: Account) =
      for {
        savedProject2 <- projectRepo.insert(project.copy(organizationId = savedProject1.organizationId))
        savedTask1 <- taskRepo.insert(task.copy(projectId = savedProject1.id, createdBy = savedAccount.id))
        savedTask2 <- taskRepo.insert(task.copy(projectId = savedProject2.id, createdBy = savedAccount.id))
        base = nowAsInstant
        entriesOfProject1 = (0 to 10).map(index =>
          logEntry.copy(
            id = UUID.randomUUID(),
            taskId = savedTask1.id,
            createdBy = savedAccount.id,
            createdAt = base.plusSeconds(index.toLong)
          )
        )
        saved <- entriesOfProject1.map(logEntryRepo.insert).toList.sequence
        _ <- createLogEntries(savedTask2.id, savedAccount.id).map(logEntryRepo.insert).toList.sequence
      } yield (saved, savedProject1)

    "find pages by project id, newest first, without duplicates" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        seeded <- seedProjectEntries(orgAndProject._2, savedAccount)
        (saved, savedProject1) = seeded
        page1 <- logEntryRepo.findPaginatedByProjectId(savedProject1.id, LogEntryFilter.empty, pageOf(5, 1))
        page2 <- logEntryRepo.findPaginatedByProjectId(savedProject1.id, LogEntryFilter.empty, pageOf(5, 2))
        page3 <- logEntryRepo.findPaginatedByProjectId(savedProject1.id, LogEntryFilter.empty, pageOf(5, 3))
        page4 <- logEntryRepo.findPaginatedByProjectId(savedProject1.id, LogEntryFilter.empty, pageOf(5, 4))
      } yield {
        val newestFirst = saved.sortBy(_.createdAt).reverse
        val cp = new Checkpoint
        cp(List(page1, page2, page3).map(_.totalNumberOfItems) shouldBe List(11L, 11L, 11L))
        cp(page1.totalNumberOfPages shouldBe 3L)
        cp(List(page1, page2, page3).map(_.items.size) shouldBe List(5, 5, 1))
        cp((page1.items ++ page2.items ++ page3.items) shouldBe newestFirst)
        cp(page4.items shouldBe empty)
        cp(page4.totalNumberOfItems shouldBe 11L)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }

    "filter by project id with an inclusive from and an exclusive to" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        seeded <- seedProjectEntries(orgAndProject._2, savedAccount)
        (saved, savedProject1) = seeded
        ordered = saved.sortBy(_.createdAt)
        window <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(from = Some(ordered(2).createdAt), to = Some(ordered(6).createdAt)),
          pageOf(100, 1)
        )
        fromOnly <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(from = Some(ordered(9).createdAt)),
          pageOf(100, 1)
        )
        toOnly <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(to = Some(ordered(1).createdAt)),
          pageOf(100, 1)
        )
      } yield {
        val cp = new Checkpoint
        cp(window.items shouldBe ordered.slice(2, 6).reverse)
        cp(window.totalNumberOfItems shouldBe 4L)
        cp(fromOnly.items shouldBe ordered.drop(9).reverse)
        cp.reportAll()
        toOnly.items shouldBe ordered.take(1)
      }

      res.transact(transactor)
    }

    "delete by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
        logEntriesToInsert = createLogEntries(savedTask.id, savedAccount.id)
        savedLogEntries <- logEntriesToInsert.map(logEntry => logEntryRepo.insert(logEntry)).toList.sequence
        numberOfDeletedLogEntries <- logEntryRepo.deleteByTaskId(savedTask.id)
        foundLogEntries <- logEntryRepo.findByTaskId(savedTask.id)
      } yield {
        val cp = new Checkpoint
        cp(numberOfDeletedLogEntries shouldBe savedLogEntries.size)
        cp.reportAll()
        foundLogEntries shouldBe empty
      }

      res.transact(transactor)
    }

  }

}
