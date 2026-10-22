package com.togger.dump

import cats.implicits._
import com.togger.account.{AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgClockImpl, TimeHelpers}
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.project.{ProjectAccount, ProjectAccountRepoImpl, ProjectError}
import com.togger.task.{Link, LinkRepoImpl, LinkVersion, LinkVersionRepoImpl, Task, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigRepoImpl, DataExportConfigService}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe.generic.auto._
import io.circe.syntax._

import java.time.LocalDate
import java.util.UUID

class DataExportStatusServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val accountRepo = new AccountRepoImpl
  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl

  val dataExportRepo = new DataExportRepo(
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
  val dataExportDiskWriter = new DataExportDiskWriter(diskOps, dumpLimit = Int.MaxValue)
  val dataExportConfigRepo = new DataExportConfigRepoImpl

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
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(7),
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

  private def newDataExportService(transactor: doobie.Transactor[cats.effect.IO]) = {
    val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
    new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)
  }

  private def insertDumpConfig(projectId: UUID, accountId: UUID, exportPath: os.Path): ConnectionIO[DataExportConfig] =
    dataExportConfigRepo.insert(
      DataExportConfig(
        id = UUID.randomUUID(),
        projectId = projectId,
        accountId = accountId,
        name = "Dump config",
        exportPath = exportPath.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  testNameFromClassName should {

    "reject status check for an account not assigned to the project" in { transactor =>
      val dataExportService = newDataExportService(transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NotAssignedToProject)
    }

    "reject status check for a project with no dump path configured" in { transactor =>
      val dataExportService = newDataExportService(transactor)

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
        result <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NoExportPathConfigured)
    }

    "report every task as in sync right after a dump" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskIds <- List.fill(2)(()).traverse(_ => createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id))
      } yield (savedAccount, savedProject, taskIds)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskIds) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        statusResult.isRight shouldBe true
        val status = statusResult.toOption.get
        status.pathStatuses.size shouldBe 1
        val pathStatus = status.pathStatuses.head
        pathStatus.exportPath.startsWith(exportBasePath.toString) shouldBe true
        pathStatus.taskStatuses.map(_.taskId).toSet shouldBe taskIds.toSet
        pathStatus.taskStatuses.foreach { taskStatus =>
          taskStatus.status shouldBe DataExportSyncStatus.InSync
          taskStatus.differences shouldBe empty
        }
      }
    }

    "flag a task out of sync when it changes after being dumped" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, taskId, savedTaskStatus.id)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId, taskStatusId) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
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
              startDate = LocalDate.now(),
              dueDate = LocalDate.now().plusDays(7),
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          )
          .transact(transactor)
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == taskId).get
        taskStatus.status shouldBe DataExportSyncStatus.OutOfSync
        taskStatus.differences shouldBe List("task versions differ")
      }
    }

    "flag a task created after the dump as missing from dump" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
      } yield (savedAccount, savedProject, savedTaskStatus.id)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskStatusId) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        newTaskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, taskStatusId).transact(transactor)
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        dumpResult.isRight shouldBe true
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == newTaskId).get
        taskStatus.status shouldBe DataExportSyncStatus.MissingFromExport
      }
    }

    "flag an orphaned dump file with no matching DB task as missing from db" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.exportPath)
        orphanTaskId = UUID.randomUUID()
        orphanDump = DataExportData(
          task = Task(id = orphanTaskId, projectId = savedProject.id, createdBy = savedAccount.id, createdAt = nowAsInstant),
          taskVersions = Nil,
          links = Nil,
          logEntries = Nil
        )
        _ = os.write.over(rootFolder / s"$orphanTaskId.json", orphanDump.asJson.spaces2)
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == orphanTaskId).get
        taskStatus.status shouldBe DataExportSyncStatus.MissingFromDb
      }
    }

    "check status against the most recent dump folder when multiple dumps exist" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, taskId)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        firstDumpFolder = os.Path(dumpResult.toOption.get.pathResults.head.exportPath)
        projectFolder = firstDumpFolder / os.up
        // A dated folder that always sorts after any real timestamp, simulating a later dump.
        laterDumpFolder = projectFolder / "99999999999999"
        orphanTaskId = UUID.randomUUID()
        orphanDump = DataExportData(
          task = Task(id = orphanTaskId, projectId = savedProject.id, createdBy = savedAccount.id, createdAt = nowAsInstant),
          taskVersions = Nil,
          links = Nil,
          logEntries = Nil
        )
        _ = os.write.over(laterDumpFolder / "project.json", os.read(firstDumpFolder / "project.json"), createFolders = true)
        _ = os.write.over(laterDumpFolder / s"$orphanTaskId.json", orphanDump.asJson.spaces2)
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        pathStatus.taskStatuses.map(_.taskId).toSet shouldBe Set(taskId, orphanTaskId)
        pathStatus.taskStatuses.find(_.taskId == taskId).get.status shouldBe DataExportSyncStatus.MissingFromExport
        pathStatus.taskStatuses.find(_.taskId == orphanTaskId).get.status shouldBe DataExportSyncStatus.MissingFromDb
      }
    }

    "flag an unparseable dump file as malformed" in { transactor =>
      val dataExportService = newDataExportService(transactor)
      val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-status-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, exportBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, taskId)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId) = setupResult
        dumpResult <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.exportPath)
        _ = os.write.over(rootFolder / s"$taskId.json", "not valid json")
        statusResult <- dataExportService.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val pathStatus = statusResult.toOption.get.pathStatuses.head
        val taskStatus = pathStatus.taskStatuses.find(_.taskId == taskId).get
        taskStatus.status shouldBe DataExportSyncStatus.Malformed
        taskStatus.differences should not be empty
      }
    }
  }

}
