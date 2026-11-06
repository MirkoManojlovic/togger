package com.togger.task

import cats.effect.IO
import com.togger.account.TgAccount
import com.togger.common.BusinessTgDbSpec
import com.togger.common.TgClockImpl
import com.togger.common.TimeHelpers
import com.togger.deletedentity.DeletedEntityRepoImpl
import com.togger.deletedentity.DeletedEntityType
import com.togger.logentry.LogEntry
import com.togger.logentry.LogEntryRepoImpl
import com.togger.logentry.LogEntryVersionRepoImpl
import com.togger.organization.TgProject
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

class TaskServiceDeleteTest extends BusinessTgDbSpec with TgAccount with TgProject with TimeHelpers {

  private val taskRepo = new TaskRepoImpl
  private val logEntryRepo = new LogEntryRepoImpl
  private val deletedEntityRepo = new DeletedEntityRepoImpl

  private def taskService(transactor: Transactor[IO]) =
    new TaskService(
      taskRepo,
      new TaskVersionRepoImpl,
      new LinkRepoImpl,
      new LinkVersionRepoImpl,
      logEntryRepo,
      new LogEntryVersionRepoImpl,
      new TaskLabelAssignmentRepoImpl,
      deletedEntityRepo,
      new TgClockImpl,
      transactor
    )

  testNameFromClassName should {

    "record a tombstone for the deleted task and for its log entries" in { transactor =>
      for {
        account <- createAccount.transact(transactor)
        orgAndProject <- createOrgAndProject.transact(transactor)
        projectId = orgAndProject._2.id
        task <- taskRepo
          .insert(Task(UUID.randomUUID(), projectId, account.id, nowAsInstant))
          .transact(transactor)
        logEntry <- logEntryRepo
          .insert(LogEntry(UUID.randomUUID(), task.id, account.id, nowAsInstant))
          .transact(transactor)
        deleted <- taskService(transactor).deleteTask(task.id)
        tombstones <- deletedEntityRepo.findAllByProjectId(projectId).transact(transactor)
      } yield {
        deleted shouldBe Right(1)
        tombstones.map(t => (t.entityType, t.entityId)).toSet shouldBe
          Set((DeletedEntityType.Task, task.id), (DeletedEntityType.LogEntry, logEntry.id))
      }
    }

    "record no tombstone when the task does not exist" in { transactor =>
      for {
        deleted <- taskService(transactor).deleteTask(UUID.randomUUID())
        orgAndProject <- createOrgAndProject.transact(transactor)
        tombstones <- deletedEntityRepo.findAllByProjectId(orgAndProject._2.id).transact(transactor)
      } yield {
        deleted shouldBe Left(TaskError.TaskNotFound)
        tombstones shouldBe empty
      }
    }

  }

}
