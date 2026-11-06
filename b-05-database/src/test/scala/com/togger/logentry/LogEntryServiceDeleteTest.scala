package com.togger.logentry

import com.togger.account.TgAccount
import com.togger.common.BusinessTgDbSpec
import com.togger.common.TgClockImpl
import com.togger.common.TimeHelpers
import com.togger.deletedentity.DeletedEntityRepoImpl
import com.togger.deletedentity.DeletedEntityType
import com.togger.logentry.LogEntryError.LogEntryNotFound
import com.togger.organization.TgProject
import com.togger.task.Task
import com.togger.task.TaskRepoImpl
import doobie.implicits._

import java.util.UUID

class LogEntryServiceDeleteTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val taskRepo = new TaskRepoImpl
  private val logEntryRepo = new LogEntryRepoImpl
  private val deletedEntityRepo = new DeletedEntityRepoImpl

  testNameFromClassName should {

    "record a tombstone in the project of the deleted log entry, and keep its task" in { transactor =>
      val service = new LogEntryService(
        logEntryRepo,
        new LogEntryVersionRepoImpl,
        taskRepo,
        deletedEntityRepo,
        new TgClockImpl,
        transactor
      )
      for {
        account <- createAccount.transact(transactor)
        orgAndProject <- createOrgAndProject.transact(transactor)
        projectId = orgAndProject._2.id
        task <- taskRepo.insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant)).transact(transactor)
        logEntry <- logEntryRepo
          .insert(LogEntry(UUID.randomUUID(), task.id, account.id, nowAsInstant))
          .transact(transactor)
        deleted <- service.deleteLogEntry(logEntry.id)
        missing <- service.deleteLogEntry(logEntry.id)
        tombstones <- deletedEntityRepo.findAllByProjectId(projectId).transact(transactor)
        remainingTask <- taskRepo.findOptionById(task.id).transact(transactor)
      } yield {
        deleted shouldBe Right(1)
        missing shouldBe Left(LogEntryNotFound)
        tombstones.map(t => (t.entityType, t.entityId)) shouldBe List((DeletedEntityType.LogEntry, logEntry.id))
        remainingTask should not be None
      }
    }

  }

}
