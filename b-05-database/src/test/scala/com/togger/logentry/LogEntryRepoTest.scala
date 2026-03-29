package com.togger.logentry
import cats.implicits.{catsSyntaxApplicativeError, toTraverseOps}
import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TimeHelpers}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.task.{Task, TaskRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

class LogEntryRepoTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val taskRepo = new TaskRepoImpl
  val logEntryRepo = new LogEntryRepoImpl

  val task = Task(
    id = -1L,
    projectId = -1L,
    name = "Task name",
    description = "Task description",
    assignedTo = None,
    taskStatusId = -1L,
    createdBy = -1L,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  val logEntry = LogEntry(
    id = -1,
    textInternal = "Text internal",
    textExternal = "Test External",
    status = LogEntryStatus.InProgress,
    taskId = -1,
    createdBy = -1,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(task =
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        savedLogEntry <- logEntryRepo.insert(logEntry.copy(taskId = savedTask.id, createdBy = savedAccount.id))
        foundLink1 <- logEntryRepo.findById(savedLogEntry.id)
        newUpdatedAt = nowAsInstant
        updatedLink <- logEntryRepo.update(savedLogEntry.copy(textExternal = "new text external"), newUpdatedAt)
        foundLink2 <- logEntryRepo.findById(savedLogEntry.id)
        _ <- logEntryRepo.deleteById(foundLink2.id)
        foundLink3 <- logEntryRepo.findById(savedLogEntry.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedLogEntry shouldBe foundLink1)
        cp(updatedLink shouldBe foundLink2)
        cp.reportAll()
        foundLink3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
        logEntriesToInsert = createLogEntries(savedTask.id, savedAccount.id)
        savedLogEntries <- logEntriesToInsert.map(logEntry => logEntryRepo.insert(logEntry)).toList.sequence
        foundLogEntries <- logEntryRepo.findByTaskId(savedTask.id)
      } yield {
        foundLogEntries shouldBe savedLogEntries
      }

      res.transact(transactor)
    }

    def createLogEntries(taskId: Long, accountId: Long) = (0 to 10).map(index =>
      LogEntry(
        id = -1,
        textInternal = s"Text internal- $index",
        textExternal = s"Test External - $index",
        status = LogEntryStatus.InProgress,
        taskId = taskId,
        createdBy = accountId,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

    "delete by task id" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        orgAndProject <- createOrgAndProject
        savedProject = orgAndProject._2
        taskStatusSaved <- createTaskStatus(savedProject.id)
        savedTask <- taskRepo.insert(
          task.copy(
            projectId = savedProject.id,
            assignedTo = Some(savedAccount.id),
            createdBy = savedAccount.id,
            taskStatusId = taskStatusSaved.id
          )
        )
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
