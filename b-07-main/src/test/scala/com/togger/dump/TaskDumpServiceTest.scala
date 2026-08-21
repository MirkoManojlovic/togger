package com.togger.dump

import cats.implicits._
import com.togger.account.TgAccount
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
import io.circe.parser.decode

import java.util.UUID

class TaskDumpServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl

  val taskDumpRepo = new TaskDumpRepo(
    projectRepo,
    projectAccountRepo,
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

  testNameFromClassName should {

    "reject dump for an account not assigned to the project" in { transactor =>
      val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, new TgClockImpl, transactor)
      val taskDumpService =
        new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, new TgClockImpl, transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        result shouldBe Left(ProjectError.NotAssignedToProject)
        os.exists(dumpBasePath) shouldBe false
      }
    }

    "reject dump for a project with no dump path configured" in { transactor =>
      val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, new TgClockImpl, transactor)
      val taskDumpService =
        new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, new TgClockImpl, transactor)
      val noConfigDumpPath: os.Path = os.pwd / "target" / s"test-task-dump-no-config-${UUID.randomUUID()}"

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
        result <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        result shouldBe Left(ProjectError.NoDumpPathConfigured)
        os.exists(noConfigDumpPath) shouldBe false
      }
    }

    "dump all tasks with links, log entries and task statuses to every configured dump path" in { transactor =>
      val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, new TgClockImpl, transactor)
      val taskDumpService =
        new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, new TgClockImpl, transactor)
      val secondDumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- taskDumpConfigRepo.insert(
          TaskDumpConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            dumpPath = dumpBasePath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        _ <- taskDumpConfigRepo.insert(
          TaskDumpConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            dumpPath = secondDumpBasePath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        savedTaskStatus <- createTaskStatus(savedProject.id)
        taskIds <- List.fill(2)(()).traverse(_ => createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id))
      } yield (savedAccount, savedProject, savedTaskStatus, taskIds)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, savedTaskStatus, taskIds) = setupResult
        result <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        result.isRight shouldBe true
        val dumpResult = result.toOption.get
        cp(dumpResult.taskCount shouldBe 2)
        cp(dumpResult.pathResults.size shouldBe 2)
        cp(dumpResult.pathResults.forall(_.success) shouldBe true)

        List(dumpBasePath, secondDumpBasePath).foreach { basePath =>
          val pathResult = dumpResult.pathResults.find(_.dumpPath.startsWith(basePath.toString)).get
          val rootFolder = os.Path(pathResult.dumpPath)
          val manifest = decode[ProjectDumpManifest](os.read(rootFolder / "project.json")).toOption.get
          cp(manifest.project.id shouldBe savedProject.id)
          cp(manifest.taskStatuses.map(_.id) shouldBe List(savedTaskStatus.id))
          cp(manifest.taskCount shouldBe 2)

          taskIds.foreach { taskId =>
            val taskDump = decode[TaskDumpData](os.read(rootFolder / s"$taskId.json")).toOption.get
            cp(taskDump.task.id shouldBe taskId)
            cp(taskDump.taskVersions.size shouldBe 2)
            cp(taskDump.links.size shouldBe 1)
            cp(taskDump.links.head.versions.size shouldBe 2)
            cp(taskDump.logEntries.size shouldBe 1)
            cp(taskDump.logEntries.head.versions.size shouldBe 2)
          }
        }
        cp.reportAll()
      }
    }
  }

  private val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-dump-${UUID.randomUUID()}"

}
