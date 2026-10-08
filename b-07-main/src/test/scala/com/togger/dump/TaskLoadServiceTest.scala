package com.togger.dump

import com.togger.account.{Account, AccountRepoImpl, AccountType, TgAccount}
import com.togger.accountmapping.{AccountMapping, AccountMappingRepoImpl}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, TgClock, TgClockImpl, TgIds, TimeHelpers}
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.project.{Project, ProjectAccount, ProjectAccountRepoImpl, ProjectError}
import com.togger.projectmapping.{ProjectMapping, ProjectMappingRepoImpl}
import com.togger.task.{
  Link,
  LinkRepoImpl,
  LinkVersion,
  LinkVersionRepoImpl,
  Task,
  TaskRepoImpl,
  TaskStatus,
  TaskVersion,
  TaskVersionRepoImpl
}
import com.togger.taskdumpconfig.{TaskDumpConfig, TaskDumpConfigRepoImpl, TaskDumpConfigService}
import com.togger.db.TgPages
import com.togger.dump.DayOfWeekCodec._
import doobie.ConnectionIO
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.syntax._

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

class TaskLoadServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

  val projectAccountRepo = new ProjectAccountRepoImpl
  val accountRepo = new AccountRepoImpl
  val taskRepo = new TaskRepoImpl
  val taskVersionRepo = new TaskVersionRepoImpl
  val linkRepo = new LinkRepoImpl
  val linkVersionRepo = new LinkVersionRepoImpl
  val logEntryRepo = new LogEntryRepoImpl
  val logEntryVersionRepo = new LogEntryVersionRepoImpl
  val projectMappingRepo = new ProjectMappingRepoImpl
  val accountMappingRepo = new AccountMappingRepoImpl

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
  val taskLoadRepo =
    new TaskLoadRepo(
      taskRepo,
      taskVersionRepo,
      linkRepo,
      linkVersionRepo,
      logEntryRepo,
      logEntryVersionRepo,
      projectMappingRepo,
      taskStatusRepo,
      new TgClockImpl,
      accountRepo,
      accountTypeRepo,
      accountMappingRepo
    )
  val diskOps = new DiskOps
  val taskDumpDiskWriter = new TaskDumpDiskWriter(diskOps, dumpLimit = Int.MaxValue)
  val taskDumpConfigRepo = new TaskDumpConfigRepoImpl

  private class TestClock extends TgClock {
    var instant: Instant = Instant.now
    override def currentInstant: Instant = instant
    override def currentOffsetDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): OffsetDateTime = instant.atOffset(zoneOffset)
    override def currentYear(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int = instant.atOffset(zoneOffset).getYear
    override def currentDate(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDate = instant.atOffset(zoneOffset).toLocalDate
  }

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
          startDate = LocalDate.now(),
          dueDate = LocalDate.now().plusDays(7),
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
        name = "Dump config",
        dumpPath = dumpPath.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  private def insertMapping(projectIdDb: UUID, projectIdDump: UUID): ConnectionIO[ProjectMapping] =
    projectMappingRepo.insert(
      ProjectMapping(
        projectIdDb = projectIdDb,
        projectIdDump = projectIdDump,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  private def insertAccountMapping(accountIdDb: UUID, accountIdDump: UUID): ConnectionIO[AccountMapping] =
    accountMappingRepo.insert(
      AccountMapping(
        accountIdDb = accountIdDb,
        accountIdDump = accountIdDump,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  private def createSecondProject(organizationId: UUID): ConnectionIO[Project] =
    projectRepo.insert(project.copy(id = UUID.randomUUID(), organizationId = organizationId, name = "Second project"))

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
        _ <- insertMapping(savedProject.id, savedProject.id)
        savedTaskStatus <- createTaskStatus(savedProject.id)
      } yield (savedAccount, savedProject, savedTaskStatus)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, savedTaskStatus) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
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
              startDate = LocalDate.now(),
              dueDate = LocalDate.now().plusDays(7),
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
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
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
        _ <- insertMapping(savedProject.id, savedProject.id)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        // simulate a local db that's behind the dump: drop the older task version, the whole link, and the older log entry version
        _ <- taskVersionRepo.deleteByVersion(fullHistory.taskVersions.head.version).transact(transactor)
        _ <- linkRepo.deleteById(fullHistory.link.id).transact(transactor)
        _ <- logEntryVersionRepo.deleteByVersion(fullHistory.logEntryVersions.head.version).transact(transactor)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
        persistedVersions <- taskVersionRepo.findByTaskId(fullHistory.taskId).transact(transactor)
        persistedLinks <- linkRepo.findByTaskId(fullHistory.taskId).transact(transactor)
        persistedLogEntryVersions <- logEntryVersionRepo.findByLogEntryId(fullHistory.logEntry.id).transact(transactor)
        reloadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
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
        _ <- insertMapping(savedProject.id, savedProject.id)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
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
        _ <- insertMapping(savedProject.id, savedProject.id)
        savedTaskStatus <- createTaskStatus(savedProject.id)
        fullHistory <- createTaskWithFullHistory(savedProject.id, savedAccount.id, savedTaskStatus.id)
      } yield (savedAccount, savedProject, fullHistory)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, fullHistory) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        _ = os.write.over(rootFolder / s"${fullHistory.taskId}.json", "not valid json")
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
      } yield {
        loadResult.isRight shouldBe true
        val taskResult = loadResult.toOption.get.taskResults.find(_.taskId == fullHistory.taskId).get
        taskResult.status shouldBe TaskLoadTaskStatus.Malformed
        taskResult.error should not be empty
      }
    }

    "reject loading when the dump path has no readable project.json manifest" in { transactor =>
      val taskLoadService = newTaskLoadService(transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo
          .insert(ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant))
          .transact(transactor)
        emptyFolder = os.pwd / "target" / s"test-task-load-empty-${UUID.randomUUID()}"
        _ = os.makeDir.all(emptyFolder)
        result <- taskLoadService.loadProject(savedProject.id, savedAccount.id, emptyFolder.toString)
      } yield result shouldBe Left(ProjectError.DumpManifestMissing)
    }

    "auto-create a project mapping and load when none exists for the target project" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (savedOrganization, dumpProject) = savedOrgAndProject
        targetProject <- createSecondProject(savedOrganization.id)
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = dumpProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = targetProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(dumpProject.id, savedAccount.id, dumpBasePath)
      } yield (savedAccount, dumpProject, targetProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, dumpProject, targetProject) = setupResult
        dumpResult <- taskDumpService.dumpProject(dumpProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        loadResult <- taskLoadService.loadProject(targetProject.id, savedAccount.id, projectRoot.toString)
        mappings <- projectMappingRepo
          .findPaginatedByProjectIdDb(targetProject.id, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        loadResult.isRight shouldBe true
        mappings.map(_.projectIdDump) shouldBe List(dumpProject.id)
      }
    }

    "auto-create an additional project mapping and load when the target project already maps to a different dump project" in {
      transactor =>
        val taskDumpService = newTaskDumpService(transactor)
        val taskLoadService = newTaskLoadService(transactor)
        val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

        val setup = for {
          savedAccount <- createAccount
          savedOrgAndProject <- createOrgAndProject
          (savedOrganization, dumpProject) = savedOrgAndProject
          targetProject <- createSecondProject(savedOrganization.id)
          unrelatedDumpProject <- createSecondProject(savedOrganization.id)
          _ <- projectAccountRepo.insert(
            ProjectAccount(projectId = dumpProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
          )
          _ <- projectAccountRepo.insert(
            ProjectAccount(projectId = targetProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
          )
          _ <- insertDumpConfig(dumpProject.id, savedAccount.id, dumpBasePath)
          _ <- insertMapping(targetProject.id, unrelatedDumpProject.id)
        } yield (savedAccount, dumpProject, targetProject, unrelatedDumpProject)

        for {
          setupResult <- setup.transact(transactor)
          (savedAccount, dumpProject, targetProject, unrelatedDumpProject) = setupResult
          dumpResult <- taskDumpService.dumpProject(dumpProject.id, savedAccount.id)
          rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
          projectRoot = rootFolder / os.up
          loadResult <- taskLoadService.loadProject(targetProject.id, savedAccount.id, projectRoot.toString)
          mappings <- projectMappingRepo
            .findPaginatedByProjectIdDb(targetProject.id, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
        } yield {
          loadResult.isRight shouldBe true
          mappings.map(_.projectIdDump).toSet shouldBe Set(unrelatedDumpProject.id, dumpProject.id)
        }
    }

    "remap the project id and reconcile task statuses by name/description when loading across a mapping" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (savedOrganization, dumpProject) = savedOrgAndProject
        targetProject <- createSecondProject(savedOrganization.id)
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = dumpProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = targetProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(dumpProject.id, savedAccount.id, dumpBasePath)
        _ <- insertMapping(targetProject.id, dumpProject.id)
        doneStatusOnDumpSide <- taskStatusRepo.insert(
          TaskStatus(
            id = UUID.randomUUID(),
            projectId = dumpProject.id,
            name = "Done",
            description = "Task completed",
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        blockedStatusOnDumpSide <- taskStatusRepo.insert(
          TaskStatus(
            id = UUID.randomUUID(),
            projectId = dumpProject.id,
            name = "Blocked",
            description = "Task blocked",
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        doneStatusOnTargetSide <- taskStatusRepo.insert(
          TaskStatus(
            id = UUID.randomUUID(),
            projectId = targetProject.id,
            name = "Done",
            description = "Task completed",
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      } yield (savedAccount, dumpProject, targetProject, doneStatusOnDumpSide, blockedStatusOnDumpSide, doneStatusOnTargetSide)

      // These tasks only ever exist as hand-written dump files, never as real rows in this DB under
      // dumpProject - a real cross-database load would never find them by id either, so this exercises
      // the "insert with remapped project id" branch of TaskLoadRepo.mergeTask, not the "merge into an
      // already-present row" branch.
      val doneTaskId = UUID.randomUUID()
      val blockedTaskId = UUID.randomUUID()

      def diskTaskFor(taskId: UUID, dumpProjectId: UUID, accountId: UUID, taskStatusId: UUID): TaskDumpData =
        TaskDumpData(
          task = Task(id = taskId, projectId = dumpProjectId, createdBy = accountId, createdAt = nowAsInstant),
          taskVersions = List(
            TaskVersion(
              version = UUID.randomUUID(),
              taskId = taskId,
              name = "Disk task",
              description = "Disk task description",
              assignedTo = None,
              closedBy = None,
              taskStatusId = taskStatusId,
              startDate = LocalDate.now(),
              dueDate = LocalDate.now().plusDays(7),
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          ),
          links = Nil,
          logEntries = Nil
        )

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, dumpProject, targetProject, doneStatusOnDumpSide, blockedStatusOnDumpSide, doneStatusOnTargetSide) =
          setupResult
        dumpResult <- taskDumpService.dumpProject(dumpProject.id, savedAccount.id)
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        _ = os.write.over(
          rootFolder / s"$doneTaskId.json",
          diskTaskFor(doneTaskId, dumpProject.id, savedAccount.id, doneStatusOnDumpSide.id).asJson.spaces2
        )
        _ = os.write.over(
          rootFolder / s"$blockedTaskId.json",
          diskTaskFor(blockedTaskId, dumpProject.id, savedAccount.id, blockedStatusOnDumpSide.id).asJson.spaces2
        )
        loadResult <- taskLoadService.loadProject(targetProject.id, savedAccount.id, projectRoot.toString)
        persistedDoneTask <- taskRepo.findById(doneTaskId).transact(transactor)
        persistedBlockedTask <- taskRepo.findById(blockedTaskId).transact(transactor)
        persistedDoneVersion <- taskVersionRepo.findByTaskId(doneTaskId).transact(transactor).map(_.head)
        persistedBlockedVersion <- taskVersionRepo.findByTaskId(blockedTaskId).transact(transactor).map(_.head)
        targetStatuses <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(targetProject.id, _)).transact(transactor)
        reloadResult <- taskLoadService.loadProject(targetProject.id, savedAccount.id, projectRoot.toString)
        targetStatusesAfterReload <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(targetProject.id, _)).transact(transactor)
      } yield {
        loadResult.isRight shouldBe true
        // both tasks and their statuses are remapped into the target project, not left pointing at the dump project
        persistedDoneTask.projectId shouldBe targetProject.id
        persistedBlockedTask.projectId shouldBe targetProject.id
        persistedDoneVersion.taskStatusId shouldBe doneStatusOnTargetSide.id
        targetStatuses.count(_.name == "Done") shouldBe 1
        val newBlockedStatus = targetStatuses.find(_.name == "Blocked").get
        newBlockedStatus.description shouldBe "Task blocked"
        persistedBlockedVersion.taskStatusId shouldBe newBlockedStatus.id

        // reloading is idempotent: no duplicate task statuses get created in the target project
        reloadResult.isRight shouldBe true
        targetStatusesAfterReload.size shouldBe targetStatuses.size
      }
    }

    "create the dumped-by account when it doesn't exist in the target db, then load" in { transactor =>
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
        _ <- insertMapping(savedProject.id, savedProject.id)
        developerAccountType <- accountTypeRepo.findByName("developer")
      } yield (savedAccount, savedProject, developerAccountType)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, developerAccountType) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
        newAccountEmail = s"new-${UUID.randomUUID().toString.take(8)}@example.com"
        manifestWithMissingAccount = originalManifest.copy(
          dumpedBy = AccountDumpData(UUID.randomUUID(), "New", "Person", newAccountEmail)
        )
        _ = os.write.over(rootFolder / "project.json", manifestWithMissingAccount.asJson.spaces2)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
        createdAccount <- accountRepo.findByEmail(newAccountEmail).transact(transactor)
        createdMappings <- accountMappingRepo
          .findPaginatedByAccountIdDump(manifestWithMissingAccount.dumpedBy.id, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        loadResult.isRight shouldBe true
        createdAccount.firstName shouldBe "New"
        createdAccount.lastName shouldBe "Person"
        createdAccount.email shouldBe newAccountEmail
        createdAccount.verified shouldBe false
        createdAccount.enabled shouldBe true
        createdAccount.accountTypeId shouldBe developerAccountType.id
        createdMappings.map(_.accountIdDb) shouldBe List(createdAccount.id)
      }
    }

    "remap account ids in a task and its log entry when the dumped-by account is found by email under a different id" in {
      transactor =>
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
          _ <- insertMapping(savedProject.id, savedProject.id)
          savedTaskStatus <- createTaskStatus(savedProject.id)
        } yield (savedAccount, savedProject, savedTaskStatus)

        for {
          setupResult <- setup.transact(transactor)
          (savedAccount, savedProject, savedTaskStatus) = setupResult
          dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
          _ = dumpResult.isRight shouldBe true
          rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
          projectRoot = rootFolder / os.up
          originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
          // dump-side id for the same person differs from their id in this db, but the email still matches
          dumpSideAccountId = UUID.randomUUID()
          manifestWithForeignAccountId = originalManifest.copy(
            dumpedBy = originalManifest.dumpedBy.copy(id = dumpSideAccountId)
          )
          _ = os.write.over(rootFolder / "project.json", manifestWithForeignAccountId.asJson.spaces2)
          newTaskId = UUID.randomUUID()
          newLogEntryId = UUID.randomUUID()
          diskData = TaskDumpData(
            task = Task(id = newTaskId, projectId = savedProject.id, createdBy = dumpSideAccountId, createdAt = nowAsInstant),
            taskVersions = List(
              TaskVersion(
                version = UUID.randomUUID(),
                taskId = newTaskId,
                name = "Disk task",
                description = "Disk task description",
                assignedTo = Some(dumpSideAccountId),
                closedBy = Some(dumpSideAccountId),
                taskStatusId = savedTaskStatus.id,
                startDate = LocalDate.now(),
                dueDate = LocalDate.now().plusDays(7),
                createdAt = nowAsInstant,
                updatedAt = nowAsInstant
              )
            ),
            links = Nil,
            logEntries = List(
              LogEntryDumpData(
                logEntry =
                  LogEntry(id = newLogEntryId, taskId = newTaskId, createdBy = dumpSideAccountId, createdAt = nowAsInstant),
                versions = List(
                  LogEntryVersion(
                    version = UUID.randomUUID(),
                    logEntryId = newLogEntryId,
                    textInternal = "internal",
                    textExternal = "external",
                    status = LogEntryStatus.InProgress,
                    createdBy = dumpSideAccountId,
                    createdAt = nowAsInstant,
                    updatedAt = nowAsInstant
                  )
                )
              )
            )
          )
          _ = os.write.over(rootFolder / s"$newTaskId.json", diskData.asJson.spaces2)
          loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
          persistedTask <- taskRepo.findById(newTaskId).transact(transactor)
          persistedVersion <- taskVersionRepo.findByTaskId(newTaskId).transact(transactor).map(_.head)
          persistedLogEntry <- logEntryRepo.findByTaskId(newTaskId).transact(transactor).map(_.head)
          persistedLogEntryVersion <- logEntryVersionRepo.findByLogEntryId(newLogEntryId).transact(transactor).map(_.head)
          createdMappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(dumpSideAccountId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
        } yield {
          loadResult.isRight shouldBe true
          persistedTask.createdBy shouldBe savedAccount.id
          persistedVersion.assignedTo shouldBe Some(savedAccount.id)
          persistedVersion.closedBy shouldBe Some(savedAccount.id)
          persistedLogEntry.createdBy shouldBe savedAccount.id
          persistedLogEntryVersion.createdBy shouldBe savedAccount.id
          createdMappings.map(_.accountIdDb) shouldBe List(savedAccount.id)
        }
    }

    "reuse an existing account mapping on a subsequent import without re-doing the email lookup" in { transactor =>
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
        _ <- insertMapping(savedProject.id, savedProject.id)
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
        dumpSideAccountId = UUID.randomUUID()
        bogusEmail = s"bogus-${UUID.randomUUID().toString.take(8)}@example.com"
        // pre-existing mapping for this dump-side id, pointing to the already-saved account
        _ <- insertAccountMapping(savedAccount.id, dumpSideAccountId).transact(transactor)
        manifestWithMappedAccount = originalManifest.copy(
          dumpedBy = AccountDumpData(dumpSideAccountId, "Bogus", "Person", bogusEmail)
        )
        _ = os.write.over(rootFolder / "project.json", manifestWithMappedAccount.asJson.spaces2)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
        bogusAccountLookup <- accountRepo.findByEmail(bogusEmail).transact(transactor).attempt
        mappingsForDumpId <- accountMappingRepo
          .findPaginatedByAccountIdDump(dumpSideAccountId, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        loadResult.isRight shouldBe true
        bogusAccountLookup.isLeft shouldBe true
        mappingsForDumpId.map(_.accountIdDb) shouldBe List(savedAccount.id)
      }
    }

    "reject the load when the dumped-by account is missing and no 'developer' account type exists" in { transactor =>
      val taskDumpService = newTaskDumpService(transactor)
      val taskLoadService = newTaskLoadService(transactor)
      val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

      val setup = for {
        managerAccountType <- accountTypeRepo.insert(
          AccountType(id = TgIds.UNASSIGNED_UUID, name = "manager", createdAt = nowAsInstant, updatedAt = nowAsInstant)
        )
        savedAccount <- accountRepo.insert(
          Account(
            id = TgIds.UNASSIGNED_UUID,
            firstName = "John",
            lastName = "Doe",
            email = email,
            password = passwordHashed,
            accountTypeId = managerAccountType.id,
            verified = false,
            enabled = true,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
        _ <- insertMapping(savedProject.id, savedProject.id)
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        _ = dumpResult.isRight shouldBe true
        rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
        projectRoot = rootFolder / os.up
        originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
        missingAccountEmail = s"missing-${UUID.randomUUID().toString.take(8)}@example.com"
        manifestWithMissingAccount = originalManifest.copy(
          dumpedBy = AccountDumpData(UUID.randomUUID(), "Missing", "Person", missingAccountEmail)
        )
        _ = os.write.over(rootFolder / "project.json", manifestWithMissingAccount.asJson.spaces2)
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
      } yield loadResult shouldBe Left(ProjectError.AccountTypeMissing)
    }

    "load only the most recent dump when several dumps exist under the project root" in { transactor =>
      val testClock = new TestClock
      val taskDumpConfigService = new TaskDumpConfigService(taskDumpConfigRepo, testClock, transactor)
      val taskDumpService = new TaskDumpService(taskDumpRepo, taskDumpDiskWriter, taskDumpConfigService, testClock, transactor)
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
        _ <- insertMapping(savedProject.id, savedProject.id)
      } yield (savedAccount, savedProject)

      def orphanDiskTask(taskId: UUID, projectId: UUID, accountId: UUID): TaskDumpData =
        TaskDumpData(
          task = Task(id = taskId, projectId = projectId, createdBy = accountId, createdAt = nowAsInstant),
          taskVersions = Nil,
          links = Nil,
          logEntries = Nil
        )

      val taskInOldDump = UUID.randomUUID()
      val taskInLatestDump = UUID.randomUUID()

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        _ = testClock.instant = Instant.parse("2026-01-01T00:00:00Z")
        firstDump <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        firstRootFolder = os.Path(firstDump.toOption.get.pathResults.head.dumpPath)
        _ = os.write.over(
          firstRootFolder / s"$taskInOldDump.json",
          orphanDiskTask(taskInOldDump, savedProject.id, savedAccount.id).asJson.spaces2
        )
        _ = testClock.instant = Instant.parse("2026-01-02T00:00:00Z")
        secondDump <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
        lastRootFolder = os.Path(secondDump.toOption.get.pathResults.head.dumpPath)
        projectRoot = lastRootFolder / os.up
        _ = os.write.over(
          lastRootFolder / s"$taskInLatestDump.json",
          orphanDiskTask(taskInLatestDump, savedProject.id, savedAccount.id).asJson.spaces2
        )
        loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
      } yield {
        loadResult.isRight shouldBe true
        val loadedTaskIds = loadResult.toOption.get.taskResults.map(_.taskId).toSet
        loadedTaskIds shouldBe Set(taskInLatestDump)
      }
    }

    "resolve account mappings for every account referenced by a task, its versions and its log entry, not just the dumper" in {
      transactor =>
        val taskDumpService = newTaskDumpService(transactor)
        val taskLoadService = newTaskLoadService(transactor)
        val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

        def createNamedAccount(firstName: String, lastName: String, emailAddress: String, accountTypeId: UUID): ConnectionIO[Account] =
          accountRepo.insert(
            Account(
              id = TgIds.UNASSIGNED_UUID,
              firstName = firstName,
              lastName = lastName,
              email = emailAddress,
              password = passwordHashed,
              accountTypeId = accountTypeId,
              verified = true,
              enabled = true,
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          )

        val setup = for {
          savedAccount <- createAccount
          developerAccountType <- accountTypeRepo.findByName("developer")
          creatorAccount <- createNamedAccount(
            "Creator",
            "Person",
            s"creator-${UUID.randomUUID().toString.take(8)}@example.com",
            developerAccountType.id
          )
          assigneeAccount <- createNamedAccount(
            "Assignee",
            "Person",
            s"assignee-${UUID.randomUUID().toString.take(8)}@example.com",
            developerAccountType.id
          )
          closerAccount <- createNamedAccount(
            "Closer",
            "Person",
            s"closer-${UUID.randomUUID().toString.take(8)}@example.com",
            developerAccountType.id
          )
          logAuthorAccount <- createNamedAccount(
            "LogAuthor",
            "Person",
            s"logauthor-${UUID.randomUUID().toString.take(8)}@example.com",
            developerAccountType.id
          )
          savedOrgAndProject <- createOrgAndProject
          (_, savedProject) = savedOrgAndProject
          _ <- projectAccountRepo.insert(
            ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
          )
          _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
          _ <- insertMapping(savedProject.id, savedProject.id)
          savedTaskStatus <- createTaskStatus(savedProject.id)
        } yield (savedAccount, creatorAccount, assigneeAccount, closerAccount, logAuthorAccount, savedProject, savedTaskStatus)

        for {
          setupResult <- setup.transact(transactor)
          (savedAccount, creatorAccount, assigneeAccount, closerAccount, logAuthorAccount, savedProject, savedTaskStatus) = setupResult
          dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
          _ = dumpResult.isRight shouldBe true
          rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
          projectRoot = rootFolder / os.up
          originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
          creatorDumpId = UUID.randomUUID()
          assigneeDumpId = UUID.randomUUID()
          closerDumpId = UUID.randomUUID()
          logAuthorDumpId = UUID.randomUUID()
          manifestWithReferencedAccounts = originalManifest.copy(
            referencedAccounts = List(
              AccountDumpData(creatorDumpId, creatorAccount.firstName, creatorAccount.lastName, creatorAccount.email),
              AccountDumpData(assigneeDumpId, assigneeAccount.firstName, assigneeAccount.lastName, assigneeAccount.email),
              AccountDumpData(closerDumpId, closerAccount.firstName, closerAccount.lastName, closerAccount.email),
              AccountDumpData(logAuthorDumpId, logAuthorAccount.firstName, logAuthorAccount.lastName, logAuthorAccount.email)
            )
          )
          _ = os.write.over(rootFolder / "project.json", manifestWithReferencedAccounts.asJson.spaces2)
          newTaskId = UUID.randomUUID()
          newLogEntryId = UUID.randomUUID()
          diskData = TaskDumpData(
            task = Task(id = newTaskId, projectId = savedProject.id, createdBy = creatorDumpId, createdAt = nowAsInstant),
            taskVersions = List(
              TaskVersion(
                version = UUID.randomUUID(),
                taskId = newTaskId,
                name = "Disk task",
                description = "Disk task description",
                assignedTo = Some(assigneeDumpId),
                closedBy = Some(closerDumpId),
                taskStatusId = savedTaskStatus.id,
                startDate = LocalDate.now(),
                dueDate = LocalDate.now().plusDays(7),
                createdAt = nowAsInstant,
                updatedAt = nowAsInstant
              )
            ),
            links = Nil,
            logEntries = List(
              LogEntryDumpData(
                logEntry =
                  LogEntry(id = newLogEntryId, taskId = newTaskId, createdBy = logAuthorDumpId, createdAt = nowAsInstant),
                versions = List(
                  LogEntryVersion(
                    version = UUID.randomUUID(),
                    logEntryId = newLogEntryId,
                    textInternal = "internal",
                    textExternal = "external",
                    status = LogEntryStatus.InProgress,
                    createdBy = logAuthorDumpId,
                    createdAt = nowAsInstant,
                    updatedAt = nowAsInstant
                  )
                )
              )
            )
          )
          _ = os.write.over(rootFolder / s"$newTaskId.json", diskData.asJson.spaces2)
          loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
          persistedTask <- taskRepo.findById(newTaskId).transact(transactor)
          persistedVersion <- taskVersionRepo.findByTaskId(newTaskId).transact(transactor).map(_.head)
          persistedLogEntry <- logEntryRepo.findByTaskId(newTaskId).transact(transactor).map(_.head)
          persistedLogEntryVersion <- logEntryVersionRepo.findByLogEntryId(newLogEntryId).transact(transactor).map(_.head)
          creatorMappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(creatorDumpId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
          assigneeMappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(assigneeDumpId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
          closerMappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(closerDumpId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
          logAuthorMappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(logAuthorDumpId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
        } yield {
          loadResult.isRight shouldBe true
          persistedTask.createdBy shouldBe creatorAccount.id
          persistedVersion.assignedTo shouldBe Some(assigneeAccount.id)
          persistedVersion.closedBy shouldBe Some(closerAccount.id)
          persistedLogEntry.createdBy shouldBe logAuthorAccount.id
          persistedLogEntryVersion.createdBy shouldBe logAuthorAccount.id
          creatorMappings.map(_.accountIdDb) shouldBe List(creatorAccount.id)
          assigneeMappings.map(_.accountIdDb) shouldBe List(assigneeAccount.id)
          closerMappings.map(_.accountIdDb) shouldBe List(closerAccount.id)
          logAuthorMappings.map(_.accountIdDb) shouldBe List(logAuthorAccount.id)
        }
    }

    "reject the whole load when a non-dumper referenced account is missing and no 'developer' account type exists" in {
      transactor =>
        val taskDumpService = newTaskDumpService(transactor)
        val taskLoadService = newTaskLoadService(transactor)
        val dumpBasePath: os.Path = os.pwd / "target" / s"test-task-load-${UUID.randomUUID()}"

        // deliberately avoid the trait's createAccount, which auto-seeds a "developer" account type
        val setup = for {
          managerAccountType <- accountTypeRepo.insert(
            AccountType(id = TgIds.UNASSIGNED_UUID, name = "manager", createdAt = nowAsInstant, updatedAt = nowAsInstant)
          )
          savedAccount <- accountRepo.insert(
            Account(
              id = TgIds.UNASSIGNED_UUID,
              firstName = "John",
              lastName = "Doe",
              email = email,
              password = passwordHashed,
              accountTypeId = managerAccountType.id,
              verified = false,
              enabled = true,
              createdAt = nowAsInstant,
              updatedAt = nowAsInstant
            )
          )
          savedOrgAndProject <- createOrgAndProject
          (_, savedProject) = savedOrgAndProject
          _ <- projectAccountRepo.insert(
            ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
          )
          _ <- insertDumpConfig(savedProject.id, savedAccount.id, dumpBasePath)
          _ <- insertMapping(savedProject.id, savedProject.id)
        } yield (savedAccount, savedProject)

        for {
          setupResult <- setup.transact(transactor)
          (savedAccount, savedProject) = setupResult
          dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
          _ = dumpResult.isRight shouldBe true
          rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
          projectRoot = rootFolder / os.up
          originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
          // the dumper resolves fine via its existing email; the referenced account does not exist and can't
          // be created because there's no 'developer' account type -- this must fail the whole load, not just
          // silently skip that one account
          missingAccountEmail = s"missing-${UUID.randomUUID().toString.take(8)}@example.com"
          manifestWithMissingReferencedAccount = originalManifest.copy(
            referencedAccounts = List(AccountDumpData(UUID.randomUUID(), "Missing", "Person", missingAccountEmail))
          )
          _ = os.write.over(rootFolder / "project.json", manifestWithMissingReferencedAccount.asJson.spaces2)
          loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
        } yield loadResult shouldBe Left(ProjectError.AccountTypeMissing)
    }

    "deduplicate an account referenced both as the dumper and in referencedAccounts, creating only one mapping" in {
      transactor =>
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
          _ <- insertMapping(savedProject.id, savedProject.id)
        } yield (savedAccount, savedProject)

        for {
          setupResult <- setup.transact(transactor)
          (savedAccount, savedProject) = setupResult
          dumpResult <- taskDumpService.dumpProject(savedProject.id, savedAccount.id)
          _ = dumpResult.isRight shouldBe true
          rootFolder = os.Path(dumpResult.toOption.get.pathResults.head.dumpPath)
          projectRoot = rootFolder / os.up
          originalManifest = taskDumpDiskWriter.readManifest(rootFolder.toString).toOption.get
          dumpSideAccountId = UUID.randomUUID()
          // the dumper is also the creator of another task on disk, so the same dump-side id
          // legitimately shows up again in referencedAccounts
          manifestWithDuplicateReference = originalManifest.copy(
            dumpedBy = originalManifest.dumpedBy.copy(id = dumpSideAccountId),
            referencedAccounts = List(originalManifest.dumpedBy.copy(id = dumpSideAccountId))
          )
          _ = os.write.over(rootFolder / "project.json", manifestWithDuplicateReference.asJson.spaces2)
          loadResult <- taskLoadService.loadProject(savedProject.id, savedAccount.id, projectRoot.toString)
          mappings <- accountMappingRepo
            .findPaginatedByAccountIdDump(dumpSideAccountId, DbPageRequest.default)
            .map(_.items)
            .transact(transactor)
        } yield {
          loadResult.isRight shouldBe true
          mappings.map(_.accountIdDb) shouldBe List(savedAccount.id)
        }
    }
  }

}
