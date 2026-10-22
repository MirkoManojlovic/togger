package com.togger.dump

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgClock, TgClockImpl, TgIds, TimeHelpers}
import com.togger.dump.DayOfWeekCodec._
import com.togger.logentry.{LogEntry, LogEntryRepoImpl, LogEntryStatus, LogEntryVersion, LogEntryVersionRepoImpl}
import com.togger.organization.{TgProject, TgTaskStatus}
import com.togger.project.{ProjectAccount, ProjectAccountRepoImpl, ProjectError}
import com.togger.task.{DefaultTaskStatus, Link, LinkRepoImpl, LinkVersion, LinkVersionRepoImpl, Task, TaskRepoImpl, TaskStatus, TaskVersion, TaskVersionRepoImpl}
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigRepoImpl, DataExportConfigService}
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

class DataExportServiceTest extends BusinessTgDbSpec with TgAccount with TgProject with TgTaskStatus with TimeHelpers {

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

  private class TestClock extends TgClock {
    var instant: Instant = Instant.now
    override def currentInstant: Instant = instant
    override def currentOffsetDateTime(zoneOffset: ZoneOffset = ZoneOffset.UTC): OffsetDateTime = instant.atOffset(zoneOffset)
    override def currentYear(zoneOffset: ZoneOffset = ZoneOffset.UTC): Int = instant.atOffset(zoneOffset).getYear
    override def currentDate(zoneOffset: ZoneOffset = ZoneOffset.UTC): LocalDate = instant.atOffset(zoneOffset).toLocalDate
  }

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

  testNameFromClassName should {

    "reject dump for an account not assigned to the project" in { transactor =>
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)

      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        result shouldBe Left(ProjectError.NotAssignedToProject)
        os.exists(exportBasePath) shouldBe false
      }
    }

    "reject dump for a project with no dump path configured" in { transactor =>
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)
      val noConfigExportPath: os.Path = os.pwd / "target" / s"test-data-export-no-config-${UUID.randomUUID()}"

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
        result <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        result shouldBe Left(ProjectError.NoExportPathConfigured)
        os.exists(noConfigExportPath) shouldBe false
      }
    }

    "dump all tasks with links, log entries and task statuses to every configured dump path" in { transactor =>
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)
      val secondDumpBasePath: os.Path = os.pwd / "target" / s"test-data-export-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- dataExportConfigRepo.insert(
          DataExportConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            name = "First dump config",
            exportPath = exportBasePath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        _ <- dataExportConfigRepo.insert(
          DataExportConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            name = "Second dump config",
            exportPath = secondDumpBasePath.toString,
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
        result <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        result.isRight shouldBe true
        val dumpResult = result.toOption.get
        cp(dumpResult.taskCount shouldBe 2)
        cp(dumpResult.pathResults.size shouldBe 2)
        cp(dumpResult.pathResults.forall(_.success) shouldBe true)

        List(exportBasePath, secondDumpBasePath).foreach { basePath =>
          val pathResult = dumpResult.pathResults.find(_.exportPath.startsWith(basePath.toString)).get
          val rootFolder = os.Path(pathResult.exportPath)
          val manifest = decode[ProjectDumpManifest](os.read(rootFolder / "project.json")).toOption.get
          cp(manifest.project.id shouldBe savedProject.id)
          cp(manifest.taskStatuses.map(_.id) shouldBe List(savedTaskStatus.id))
          cp(manifest.dumpedBy.id shouldBe savedAccount.id)
          cp(manifest.dumpedBy.firstName shouldBe savedAccount.firstName)
          cp(manifest.dumpedBy.lastName shouldBe savedAccount.lastName)
          cp(manifest.dumpedBy.email shouldBe savedAccount.email)
          cp(manifest.taskCount shouldBe 2)

          taskIds.foreach { taskId =>
            val dataExport = decode[DataExportData](os.read(rootFolder / s"$taskId.json")).toOption.get
            cp(dataExport.task.id shouldBe taskId)
            cp(dataExport.taskVersions.size shouldBe 2)
            cp(dataExport.links.size shouldBe 1)
            cp(dataExport.links.head.versions.size shouldBe 2)
            cp(dataExport.logEntries.size shouldBe 1)
            cp(dataExport.logEntries.head.versions.size shouldBe 2)
          }
        }
        cp.reportAll()
      }
    }

    "exclude tasks with an Archived task status from the dump" in { transactor =>
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)
      val archivedDumpBasePath: os.Path = os.pwd / "target" / s"test-data-export-archived-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- dataExportConfigRepo.insert(
          DataExportConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            name = "Dump config",
            exportPath = archivedDumpBasePath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        activeTaskStatus <- createTaskStatus(savedProject.id)
        archivedTaskStatus <- taskStatusRepo.insert(
          taskStatus.copy(id = UUID.randomUUID(), projectId = savedProject.id, name = DefaultTaskStatus.Archived.statusName)
        )
        activeTaskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, activeTaskStatus.id)
        archivedTaskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, archivedTaskStatus.id)
      } yield (savedAccount, savedProject, activeTaskId, archivedTaskId)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, activeTaskId, archivedTaskId) = setupResult
        result <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        result.isRight shouldBe true
        val dumpResult = result.toOption.get
        cp(dumpResult.taskCount shouldBe 1)
        val rootFolder = os.Path(dumpResult.pathResults.head.exportPath)
        cp(os.exists(rootFolder / s"$activeTaskId.json") shouldBe true)
        cp(os.exists(rootFolder / s"$archivedTaskId.json") shouldBe false)
        cp.reportAll()
      }
    }

    "delete the oldest dump folders once the number of dumps exceeds dump-limit" in { transactor =>
      val testClock = new TestClock
      val limitedDumpDiskWriter = new DataExportDiskWriter(diskOps, dumpLimit = 2)
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, testClock, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, limitedDumpDiskWriter, dataExportConfigService, testClock, transactor)
      val limitedExportPath: os.Path = os.pwd / "target" / s"test-data-export-limit-${UUID.randomUUID()}"

      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- dataExportConfigRepo.insert(
          DataExportConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            name = "Dump config",
            exportPath = limitedExportPath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        _ = testClock.instant = Instant.parse("2026-01-01T00:00:00Z")
        first <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        _ = testClock.instant = Instant.parse("2026-01-02T00:00:00Z")
        second <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
        _ = testClock.instant = Instant.parse("2026-01-03T00:00:00Z")
        third <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        cp(first.isRight shouldBe true)
        cp(second.isRight shouldBe true)
        cp(third.isRight shouldBe true)

        val projectFolder = os.Path(third.toOption.get.pathResults.head.exportPath) / os.up
        val remainingDumpFolders = os.list(projectFolder).map(_.last).toSet
        cp(remainingDumpFolders shouldBe Set("20260102000000", "20260103000000"))
        cp.reportAll()
      }
    }

    "include every account referenced by a project's tasks in referencedAccounts, deduplicated" in { transactor =>
      val dataExportConfigService = new DataExportConfigService(dataExportConfigRepo, new TgClockImpl, transactor)
      val dataExportService =
        new DataExportService(dataExportRepo, dataExportDiskWriter, dataExportConfigService, new TgClockImpl, transactor)

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
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(
          ProjectAccount(projectId = savedProject.id, accountId = savedAccount.id, createdAt = nowAsInstant)
        )
        _ <- dataExportConfigRepo.insert(
          DataExportConfig(
            id = UUID.randomUUID(),
            projectId = savedProject.id,
            accountId = savedAccount.id,
            name = "Dump config",
            exportPath = exportBasePath.toString,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        savedTaskStatus <- createTaskStatus(savedProject.id)
        firstTask <- taskRepo.insert(
          Task(id = UUID.randomUUID(), projectId = savedProject.id, createdBy = creatorAccount.id, createdAt = nowAsInstant)
        )
        _ <- taskVersionRepo.insert(
          TaskVersion(
            version = UUID.randomUUID(),
            taskId = firstTask.id,
            name = "Task one",
            description = "Task one description",
            assignedTo = Some(assigneeAccount.id),
            closedBy = Some(closerAccount.id),
            taskStatusId = savedTaskStatus.id,
            startDate = LocalDate.now(),
            dueDate = LocalDate.now().plusDays(7),
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
        // second task created by the same account as the first -- must not produce a duplicate entry
        secondTask <- taskRepo.insert(
          Task(id = UUID.randomUUID(), projectId = savedProject.id, createdBy = creatorAccount.id, createdAt = nowAsInstant)
        )
        _ <- taskVersionRepo.insert(
          TaskVersion(
            version = UUID.randomUUID(),
            taskId = secondTask.id,
            name = "Task two",
            description = "Task two description",
            assignedTo = None,
            closedBy = None,
            taskStatusId = savedTaskStatus.id,
            startDate = LocalDate.now(),
            dueDate = LocalDate.now().plusDays(7),
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      } yield (savedAccount, creatorAccount, assigneeAccount, closerAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, creatorAccount, assigneeAccount, closerAccount, savedProject) = setupResult
        result <- dataExportService.dumpProject(savedProject.id, savedAccount.id)
      } yield {
        result.isRight shouldBe true
        val rootFolder = os.Path(result.toOption.get.pathResults.head.exportPath)
        val manifest = decode[ProjectDumpManifest](os.read(rootFolder / "project.json")).toOption.get
        val referencedIds = manifest.referencedAccounts.map(_.id)
        referencedIds.toSet shouldBe Set(creatorAccount.id, assigneeAccount.id, closerAccount.id)
        referencedIds.size shouldBe referencedIds.distinct.size
      }
    }

    "omit accounts that no longer exist when gathering referenced accounts" in { transactor =>
      val missingAccountId = UUID.randomUUID()
      val diskData = DataExportData(
        task = Task(id = UUID.randomUUID(), projectId = UUID.randomUUID(), createdBy = missingAccountId, createdAt = nowAsInstant),
        taskVersions = Nil,
        links = Nil,
        logEntries = Nil
      )

      dataExportRepo.getReferencedAccounts(List(diskData)).transact(transactor).map(_ shouldBe Nil)
    }

    // circe's generic-auto derivation requires every field to be present and does not fall back to the case
    // class's default value when a key is missing -- so a pre-fix manifest (no referencedAccounts key) fails
    // to decode cleanly rather than silently loading with an empty list. That's an accepted outcome: affected
    // projects are simply re-dumped after upgrading.
    "fail to decode a manifest json missing the referencedAccounts field, rather than silently defaulting to empty" in {
      transactor =>
        val manifest = ProjectDumpManifest(
          project = project,
          taskStatuses = Nil,
          dumpedBy = AccountDumpData(UUID.randomUUID(), "John", "Doe", "john@example.com"),
          referencedAccounts = Nil,
          dumpedAt = Instant.now,
          taskCount = 0
        )
        val jsonWithoutReferencedAccounts = manifest.asJson.hcursor.downField("referencedAccounts").delete.top.get
        val decoded = decode[ProjectDumpManifest](jsonWithoutReferencedAccounts.noSpaces)

        decoded.isLeft.pure[ConnectionIO].transact(transactor).map(_ shouldBe true)
    }
  }

  private val exportBasePath: os.Path = os.pwd / "target" / s"test-data-export-${UUID.randomUUID()}"

}
