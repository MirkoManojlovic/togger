package com.togger.dump

import com.togger.account.TgAccount
import com.togger.common.{BusinessTgDbSpec, TgClockImpl, TimeHelpers}
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.project.{ProjectAccount, ProjectAccountRepoImpl, ProjectError}
import com.togger.task.{Link, LinkRepoImpl, LinkVersion, LinkVersionRepoImpl, Task, TaskRepoImpl, TaskVersion, TaskVersionRepoImpl}
import com.togger.taskdumpconfig.{TaskDumpConfig, TaskDumpConfigRepoImpl, TaskDumpConfigService}
import doobie.ConnectionIO
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.syntax._

import java.util.UUID

class TaskLoadServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

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
  val taskLoadRepo =
    new TaskLoadRepo(taskRepo, taskVersionRepo, linkRepo, linkVersionRepo, logEntryRepo, logEntryVersionRepo)
  val diskOps = new DiskOps
  val taskDumpDiskWriter = new TaskDumpDiskWriter(diskOps)
  val taskDumpConfigRepo = new TaskDumpConfigRepoImpl

  case class FullHistory(
    taskId: UUID,
    taskVersions: List[TaskVersion],
    link: Link,
    linkVersions: List[LinkVersion],
    logEntry: LogEntry,
    logEntryVersions: List[LogEntryVersion]
  )

  def createTaskWithFullHistory(projectId: UUID, createdBy: UUID, taskStatusId: UUID): ConnectionIO[FullHistory] =
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
      secondVersion <- taskVersionRepo.insert(firstVersion.copy(name = "Task name v2", description = "Task description v2"))
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
      secondLinkVersion <- linkVersionRepo.insert(firstLinkVersion.copy(name = "Link v2"))
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
      secondLogEntryVersion <- logEntryVersionRepo.insert(firstLogEntryVersion.copy(textInternal = "internal v2"))
    } yield FullHistory(
      taskId = savedTask.id,
      taskVersions = List(firstVersion, secondVersion),
      link = savedLink,
      linkVersions = List(firstLinkVersion, secondLinkVersion),
      logEntry = savedLogEntry,
      logEntryVersions = List(firstLogEntryVersion, secondLogEntryVersion)
    )

  private def newTaskLoadService(transactor: doobie.Transactor[cats.effect.IO]) =
    new TaskLoadService(taskDumpRepo, taskLoadRepo, taskDumpDiskWriter, transactor)

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
        dumpPath = dumpPath.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  testNameFromClassName should {

    "reject load for an account not assigned to the project" in { transactor =>
      val taskLoadService = newTaskLoadService(transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- taskLoadService.loadProject(savedProject.id, savedAccount.id, (os.pwd / "target").toString)
      } yield result shouldBe Left(ProjectError.NotAssignedToProject)
    }

    "insert a task that exists on disk but not in the db" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
      } yield (savedAccount, savedProject, savedTaskStatus)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, savedTaskStatus) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        newTaskId = UUID.randomUUID()
        newLinkId = UUID.randomUUID()
        newLogEntryId = UUID.randomUUID()
        diskData = TaskDumpData(
          task = Task(id = newTaskId, projectId = savedProject.id, createdBy = savedAccount.id, createdAt = nowAsInstant),
          taskVersions = List(
            TaskVersion(
              version = UUID.randomUUID(),
              taskId = newTaskId,
              name = "Disk task",
              description = "Disk task description",
              assignedTo = None,
              closedBy = None,
              taskStatusId = savedTaskStatus.id,
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          ),
          links = List(
            LinkDumpData(
              link = Link(id = newLinkId, taskId = newTaskId, createdAt = nowAsInstant),
              versions = List(
                LinkVersion(
                  version = UUID.randomUUID(),
                  linkId = newLinkId,
                  name = "Disk link",
                  url = "https://example.com",
                  createdAt = nowAsInstant,
                  updatedAt = nowAsInstant
                )
              )
            )
          ),
          logEntries = List(
            LogEntryDumpData(
              logEntry = LogEntry(id = newLogEntryId, taskId = newTaskId, createdBy = savedAccount.id, createdAt = nowAsInstant),
              versions = List(
                LogEntryVersion(
                  version = UUID.randomUUID(),
                  logEntryId = newLogEntryId,
                  textInternal = "internal",
                  textExternal = "external",
                  status = LogEntryStatus.InProgress,
                  createdBy = savedAccount.id,
                  createdAt = nowAsInstant,
                  updatedAt = nowAsInstant
                )
              )
            )
          )
        )
        _ = os.write.over(rootFolder / s"$newTaskId.json", diskData.asJson.spaces2)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)
        persistedTask <- taskRepo.findById(newTaskId).transact(transactor)
        persistedVersions <- taskVersionRepo.findByTaskId(newTaskId).transact(transactor)
        persistedLinks <- linkRepo.findByTaskId(newTaskId).transact(transactor)
        persistedLogEntries <- logEntryRepo.findByTaskId(newTaskId).transact(transactor)
      } yield {
        loadResult.isRight shouldBe true
        val taskResult = loadResult.toOption.get.taskResults.find(_.taskId == newTaskId).get
        taskResult.status shouldBe TaskLoadTaskStatus.Inserted
        taskResult.versionsAdded shouldBe 3
        taskResult.linksAdded shouldBe 1
        taskResult.logEntriesAdded shouldBe 1
        persistedTask.id shouldBe newTaskId
        persistedVersions.size shouldBe 1
        persistedLinks.size shouldBe 1
        persistedLogEntries.size shouldBe 1
      }
    }

    "merge missing versions and links into a task that already exists in the db, and be idempotent on reload" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        // simulate a local db that's behind the dump: drop the older task version, the whole link, and the older log entry version
        _ <- taskVersionRepo.deleteByVersion(fullHistory.taskVersions.head.version).transact(transactor)
        _ <- linkRepo.deleteById(fullHistory.link.id).transact(transactor)
        _ <- logEntryVersionRepo.deleteByVersion(fullHistory.logEntryVersions.head.version).transact(transactor)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)
        persistedVersions <- taskVersionRepo.findByTaskId(fullHistory.taskId).transact(transactor)
        persistedLinks <- linkRepo.findByTaskId(fullHistory.taskId).transact(transactor)
        persistedLogEntryVersions <- logEntryVersionRepo.findByLogEntryId(fullHistory.logEntry.id).transact(transactor)
        reloadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)
      } yield {
        loadResult.isRight shouldBe true
        val taskResult = loadResult.toOption.get.taskResults.find(_.taskId == fullHistory.taskId).get
        taskResult.status shouldBe TaskLoadTaskStatus.Merged
        taskResult.versionsAdded shouldBe 4 // 1 task version + 2 link versions (whole link re-inserted) + 1 log entry version
        taskResult.linksAdded shouldBe 1
        taskResult.logEntriesAdded shouldBe 0
        persistedVersions.size shouldBe 2
        persistedLinks.size shouldBe 1
        persistedLogEntryVersions.size shouldBe 2

        reloadResult.isRight shouldBe true
        val reloadTaskResult = reloadResult.toOption.get.taskResults.find(_.taskId == fullHistory.taskId).get
        reloadTaskResult.status shouldBe TaskLoadTaskStatus.Unchanged
        reloadTaskResult.versionsAdded shouldBe 0
        reloadTaskResult.linksAdded shouldBe 0
        reloadTaskResult.logEntriesAdded shouldBe 0
      }
    }

    "report a task with no new data on disk as unchanged" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)
      } yield {
        loadResult.isRight shouldBe true
        val taskResult = loadResult.toOption.get.taskResults.find(_.taskId == fullHistory.taskId).get
        taskResult.status shouldBe TaskLoadTaskStatus.Unchanged
        taskResult.versionsAdded shouldBe 0
        taskResult.linksAdded shouldBe 0
        taskResult.logEntriesAdded shouldBe 0
      }
    }

    "flag an unparseable dump file as malformed without failing the rest of the batch" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        _ = os.write.over(rootFolder / s"${fullHistory.taskId}.json", "not valid json")
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, dumpBasePath.toString)
      } yield {
        loadResult.isRight shouldBe true
        val taskResult = loadResult.toOption.get.taskResults.find(_.taskId == fullHistory.taskId).get
        taskResult.status shouldBe TaskLoadTaskStatus.Malformed
        taskResult.error should not be empty
      }
    }
  }

}
