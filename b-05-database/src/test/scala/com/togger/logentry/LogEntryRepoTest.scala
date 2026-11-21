package com.togger.logentry
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.account.TgAccount
import com.togger.common.BusinessTgDbSpec
import com.togger.common.DbPageRequest
import com.togger.common.PageRequest
import com.togger.common.TgIds
import com.togger.common.TimeHelpers
import com.togger.dto.LogEntryFilter
import com.togger.organization.TgProject
import com.togger.organization.TgTaskStatus
import com.togger.project.Project
import com.togger.task.Task
import com.togger.task.TaskRepoImpl
import doobie.implicits._

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
    createdAt = nowAsInstant,
    logDate = nowAsLocalDate
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
        createdAt = nowAsInstant,
        logDate = nowAsLocalDate.minusDays(index.toLong)
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
            createdAt = base.plusSeconds(index.toLong),
            logDate = nowAsLocalDate.plusDays(index.toLong)
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
        val newestFirst = saved.sortBy(_.logDate).reverse
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

    "filter by project id with an inclusive from and an inclusive to" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        seeded <- seedProjectEntries(orgAndProject._2, savedAccount)
        (saved, savedProject1) = seeded
        ordered = saved.sortBy(_.logDate)
        window <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(from = Some(ordered(2).logDate), to = Some(ordered(6).logDate)),
          pageOf(100, 1)
        )
        fromOnly <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(from = Some(ordered(9).logDate)),
          pageOf(100, 1)
        )
        toOnly <- logEntryRepo.findPaginatedByProjectId(
          savedProject1.id,
          LogEntryFilter(to = Some(ordered(1).logDate)),
          pageOf(100, 1)
        )
      } yield {
        val cp = new Checkpoint
        cp(window.items shouldBe ordered.slice(2, 7).reverse)
        cp(window.totalNumberOfItems shouldBe 5L)
        cp(fromOnly.items shouldBe ordered.drop(9).reverse)
        cp.reportAll()
        toOnly.items shouldBe ordered.take(2).reverse
      }

      res.transact(transactor)
    }

    "move a log entry to another log date" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedTask <- taskRepo.insert(task.copy(projectId = orgAndProject._2.id, createdBy = savedAccount.id))
        savedLogEntry <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
        newDate = savedLogEntry.logDate.plusDays(2)
        updated <- logEntryRepo.updateLogDate(savedLogEntry.id, newDate)
        found <- logEntryRepo.findById(savedLogEntry.id)
      } yield {
        updated shouldBe 1
        found shouldBe savedLogEntry.copy(logDate = newDate)
      }

      res.transact(transactor)
    }

    "tell whether the task has an entry for a log date, also excluding one entry" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedTask <- taskRepo.insert(task.copy(projectId = orgAndProject._2.id, createdBy = savedAccount.id))
        savedLogEntry <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
        onItsDay <- logEntryRepo.existsByTaskIdAndLogDate(savedTask.id, savedLogEntry.logDate)
        onOtherDay <- logEntryRepo.existsByTaskIdAndLogDate(savedTask.id, savedLogEntry.logDate.plusDays(1))
        excludingItself <- logEntryRepo
          .existsByTaskIdAndLogDateExcluding(savedTask.id, savedLogEntry.logDate, savedLogEntry.id)
        excludingOther <- logEntryRepo
          .existsByTaskIdAndLogDateExcluding(savedTask.id, savedLogEntry.logDate, UUID.randomUUID())
      } yield {
        val cp = new Checkpoint
        cp(onItsDay shouldBe true)
        cp(onOtherDay shouldBe false)
        cp(excludingItself shouldBe false)
        cp.reportAll()
        excludingOther shouldBe true
      }

      res.transact(transactor)
    }

    "refuse a second entry of the task on the same log date" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedTask <- taskRepo.insert(task.copy(projectId = orgAndProject._2.id, createdBy = savedAccount.id))
        _ <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
        duplicate <- logEntryRepo
          .insert(logEntry.copy(id = UUID.randomUUID(), taskId = savedTask.id, createdBy = savedAccount.id))
          .attempt
      } yield duplicate.isLeft shouldBe true

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
