package com.togger.logentry
import cats.implicits.catsSyntaxApplicativeError
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class LogEntryVersionRepoTest
    extends BusinessTgDbSpec
    with TgAccount
    with TgProject
    with TgTaskStatus
    with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl

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

  val v1 = "v1"
  val v2 = "v2"

  def logEntryVersion(logEntryId: UUID, accountId: UUID, suffix: String) = LogEntryVersion(
    version = UUID.randomUUID(),
    logEntryId = logEntryId,
    textInternal = s"Text internal $suffix",
    textExternal = s"Text external $suffix",
    status = LogEntryStatus.InProgress,
    createdBy = accountId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createTaskAndLogEntry() =
    for {
      savedAccount <- createAccount
      orgAndProject <- createOrgAndProject
      savedProject = orgAndProject._2
      savedTask <- taskRepo.insert(task.copy(projectId = savedProject.id, createdBy = savedAccount.id))
      savedLogEntry <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
    } yield (savedAccount, savedLogEntry)

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        accountAndEntry <- createTaskAndLogEntry()
        (savedAccount, savedLogEntry) = accountAndEntry
        savedVersion <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v1))
        foundVersion1 <- logEntryVersionRepo.findByVersion(savedVersion.version)
        updatedVersion <- logEntryVersionRepo.updateContent(savedVersion.copy(textExternal = "new text external"))
        foundVersion2 <- logEntryVersionRepo.findByVersion(savedVersion.version)
        _ <- logEntryVersionRepo.deleteByVersion(foundVersion2.version)
        foundVersion3 <- logEntryVersionRepo.findByVersion(savedVersion.version).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedVersion shouldBe foundVersion1)
        cp(updatedVersion shouldBe foundVersion2)
        cp(updatedVersion.textExternal shouldBe "new text external")
        cp.reportAll()
        foundVersion3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by log entry id, return newest first, and find the latest" in { transactor =>
      val res = for {
        accountAndEntry <- createTaskAndLogEntry()
        (savedAccount, savedLogEntry) = accountAndEntry
        logEntryVersion1 <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v1))
        logEntryVersion2 <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v2))
        logEntryVersion3 <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, "v3"))
        foundVersions <- logEntryVersionRepo.findByLogEntryId(savedLogEntry.id)
        latest <- logEntryVersionRepo.findLatestByLogEntryId(savedLogEntry.id)
      } yield {
        val cp = new Checkpoint
        cp(
          foundVersions should contain theSameElementsInOrderAs List(
            logEntryVersion3,
            logEntryVersion2,
            logEntryVersion1
          )
        )
        cp.reportAll()
        latest shouldBe logEntryVersion3
      }

      res.transact(transactor)
    }

    "count versions for a log entry" in { transactor =>
      val res = for {
        accountAndEntry <- createTaskAndLogEntry()
        (savedAccount, savedLogEntry) = accountAndEntry
        _ <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v1))
        _ <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v2))
        count <- logEntryVersionRepo.countByLogEntryId(savedLogEntry.id)
      } yield count shouldBe 2

      res.transact(transactor)
    }

    "delete versions when the log entry is deleted" in { transactor =>
      val res = for {
        accountAndEntry <- createTaskAndLogEntry()
        (savedAccount, savedLogEntry) = accountAndEntry
        _ <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v1))
        _ <- logEntryVersionRepo.insert(logEntryVersion(savedLogEntry.id, savedAccount.id, v2))
        _ <- logEntryRepo.deleteById(savedLogEntry.id)
        remainingVersions <- logEntryVersionRepo.findByLogEntryId(savedLogEntry.id)
      } yield remainingVersions shouldBe empty

      res.transact(transactor)
    }

  }

}
