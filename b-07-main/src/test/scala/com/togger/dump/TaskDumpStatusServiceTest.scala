package com.togger.dump

import cats.implicits._
import com.togger.account.{AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgClockImpl, TimeHelpers}
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.project.{ProjectAccount, ProjectAccountRepoImpl, ProjectError}
import com.togger.task.{Link, LinkRepoImpl, LinkVersion, LinkVersionRepoImpl, Task, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import com.togger.taskdumpconfig.{TaskDumpConfig, TaskDumpConfigRepoImpl, TaskDumpConfigService}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe.generic.auto._
import io.circe.syntax._

import java.util.UUID

class TaskDumpStatusServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val accountRepo = new AccountRepoImpl
  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl

  val taskDumpRepo = new TaskDumpRepo(
    projectRepo,
    projectAccountRepo,
    accountRepo,
    taskRepo,
    taskVersionRepo,
    taskStatusRepo,
    linkRepo,
    linkVersionRepo,
    logEntryRepo,
    logEntryVersionRepo
  )
  val diskOps = new DiskOps
  val taskDumpDiskWriter = new TaskDumpDiskWriter(diskOps)
  val taskDumpConfigRepo = new TaskDumpConfigRepoImpl

  def createTaskWithFullHistory(projectId: UUID, createdBy: UUID, taskStatusId: UUID): ConnectionIO[UUID] =
    for {
      savedTask <- taskRepo.insert(Task(id = UUID.randomUUID(), projectId = projectId, createdBy = createdBy, createdAt = nowAsInstant))
      firstVersion <- taskVersionRepo.insert(
        TaskVersion(
          version = UUID.randomUUID(),
          taskId = savedTask.id,
          name = "Task name",
          description = "Task description",
          assignedTo = None,
          closedBy = None,
          taskStatusId = taskStatusId,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- taskVersionRepo.insert(firstVersion.copy(name = "Task name v2", description = "Task description v2"))
      savedLink <- linkRepo.insert(Link(id = UUID.randomUUID(), taskId = savedTask.id, createdAt = nowAsInstant))
      firstLinkVersion <- linkVersionRepo.insert(
        LinkVersion(
          version = UUID.randomUUID(),
          linkId = savedLink.id,
          name = "Link",
          url = "https://example.com",
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- linkVersionRepo.insert(firstLinkVersion.copy(name = "Link v2"))
      savedLogEntry <- logEntryRepo.insert(LogEntry(id = UUID.randomUUID(), taskId = savedTask.id, createdBy = createdBy, createdAt = nowAsInstant))
      firstLogEntryVersion <- logEntryVersionRepo.insert(
        LogEntryVersion(
          version = UUID.randomUUID(),
          logEntryId = savedLogEntry.id,
          textInternal = "internal",
          textExternal = "external",
          status = LogEntryStatus.InProgress,
          createdBy = createdBy,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
      _ <- logEntryVersionRepo.insert(firstLogEntryVersion.copy(textInternal = "internal v2"))
    } yield savedTask.id

  private def newTaskDumpService(transactor: doobie.Transactor[cats.effect.IO]) = {
    val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, new TgClockImpl, transactor)
    new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, new TgClockImpl, transactor)
  }

  private def insertDumpConfig(projectId: UUID, accountId: UUID, dumpPath: os.Path): ConnectionIO[TaskDumpConfig] =
    taskDumpConfigRepo.insert(
      TaskDumpConfig(
        id = UUID.randomUUID(),
        projectId = projectId,
        accountId = accountId,
        name = "Dump config",
        dumpPath = dumpPath.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  testNameFromClassName should {

    "reject status check for an account not assigned to the project" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NotAssignedToProject)
    }

    "reject status check for a project with no dump path configured" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        result <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NoDumpPathConfigured)
    }

    "report every task as in sync right after a dump" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskIds <- List.fill(2)(()).traverse(_ => createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id))
      } yield (savedAccount, savedProject, taskIds)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskIds) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        statusResult <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        statusResult.isRight shouldBe true
        val status = statusResult.toOption.get
        status.pathStatuses.size shouldBe 1
        val pathStatus = status.pathStatuses.head
        pathStatus.dumpPath.startsWith(dumpBasePath.toString) shouldBe true
        pathStatus.taskStatuses.map(_.taskId).toSet shouldBe taskIds.toSet
        pathStatus.taskStatuses.foreach { taskStatus =>
          taskStatus.status shouldBe TaskDumpSyncStatus.InSync
          taskStatus.differences shouldBe empty
        }
      }
    }

    "flag a task out of sync when it changes after being dumped" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, taskId, savedTaskStatus.id)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId, taskStatusId) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ <- taskVersionRepo
          .insert(
            TaskVersion(
              version = UUID.randomUUID(),
              taskId = taskId,
              name = "Task name v3",
              description = "Task description v3",
              assignedTo = None,
              closedBy = None,
              taskStatusId = taskStatusId,
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          )
          .transact(transactor)
        statusResult <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == taskId).get
        taskStatus.status shouldBe TaskDumpSyncStatus.OutOfSync
        taskStatus.differences shouldBe List("task versions differ")
      }
    }

    "flag a task created after the dump as missing from dump" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
      } yield (savedAccount, savedProject, savedTaskStatus.id)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskStatusId) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        newTaskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, taskStatusId).transact(transactor)
        statusResult <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == newTaskId).get
        taskStatus.status shouldBe TaskDumpSyncStatus.MissingFromDump
      }
    }

    "flag an orphaned dump file with no matching DB task as missing from db" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        orphanTaskId = UUID.randomUUID()
        orphanDump = TaskDumpData(
          task = Task(id = orphanTaskId, projectId = savedProject.id, createdBy = savedAccount.id, createdAt = nowAsInstant),
          taskVersions = Nil,
          links = Nil,
          logEntries = Nil
        )
        _ = os.write.over(rootFolder / s"$orphanTaskId.json", orphanDump.asJson.spaces2)
        statusResult <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == orphanTaskId).get
        taskStatus.status shouldBe TaskDumpSyncStatus.MissingFromDb
      }
    }

    "flag an unparseable dump file as malformed" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, taskId)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        _ = os.write.over(rootFolder / s"$taskId.json", "not valid json")
        statusResult <- taskDumpService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == taskId).get
        taskStatus.status shouldBe TaskDumpSyncStatus.Malformed
        taskStatus.differences should not be empty
      }
    }
  }

}
