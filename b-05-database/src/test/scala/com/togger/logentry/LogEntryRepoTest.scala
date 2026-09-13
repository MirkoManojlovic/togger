package com.togger.logentry
import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
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

    "find by project id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject1 <- createOrgAndProject
        savedProject1 = orgAndProject1._2
        savedProject2 <- projectRepo.insert(project.copy(organizationId = orgAndProject1._1.id))
        savedTask1 <- taskRepo.insert(task.copy(projectId = savedProject1.id, createdBy = savedAccount.id))
        savedTask2 <- taskRepo.insert(task.copy(projectId = savedProject2.id, createdBy = savedAccount.id))
        logEntriesForProject1 = createLogEntries(savedTask1.id, savedAccount.id)
        logEntriesForProject2 = createLogEntries(savedTask2.id, savedAccount.id)
        savedLogEntriesForProject1 <- logEntriesForProject1.map(logEntryRepo.insert).toList.sequence
        _ <- logEntriesForProject2.map(logEntryRepo.insert).toList.sequence
        foundLogEntries <- logEntryRepo.findByProjectId(savedProject1.id)
      } yield foundLogEntries.toSet shouldBe savedLogEntriesForProject1.toSet

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
