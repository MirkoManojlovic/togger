package com.togger.dump

import cats.implicits._
import com.togger.account.{Account, AccountType}
import com.togger.accountmapping.AccountMapping
import com.togger.common.{DbPageRequest, TgIds}
import com.togger.db.TgPages
import com.togger.project.{ProjectAccount, ProjectError}
import com.togger.projectmapping.ProjectMapping
import com.togger.task.TaskStatus
import doobie.ConnectionIO
import doobie.implicits._
import io.circe.generic.auto._

import java.util.UUID

/** Project and account remapping when an export is imported into another project or database. */
class DataImportMappingTest extends ExportImportTestSupport {

  private def assign(projectId: UUID, accountId: UUID): ConnectionIO[ProjectAccount] =
    projectAccountRepo.insert(ProjectAccount(projectId, accountId, nowAsInstant))

  private def insertProjectMapping(projectIdDb: UUID, projectIdDump: UUID): ConnectionIO[ProjectMapping] =
    projectMappingRepo.insert(ProjectMapping(projectIdDb, projectIdDump, nowAsInstant, nowAsInstant))

  private def insertAccountMapping(accountIdDb: UUID, accountIdDump: UUID): ConnectionIO[AccountMapping] =
    accountMappingRepo.insert(AccountMapping(accountIdDb, accountIdDump, nowAsInstant, nowAsInstant))

  private def accountMappingsOf(accountIdDump: UUID): ConnectionIO[List[AccountMapping]] =
    accountMappingRepo.findPaginatedByAccountIdDump(accountIdDump, DbPageRequest.default).map(_.items.toList)

  private def uniqueEmail(prefix: String) = s"$prefix-${UUID.randomUUID().toString.take(8)}@example.com"

  /** An account of a type other than "developer", so that creating a missing account must fail. */
  private def createAccountWithoutDeveloperType: ConnectionIO[Account] =
    for {
      managerType <- accountTypeRepo.insert(
        AccountType(id = TgIds.UNASSIGNED_UUID, name = "manager", createdAt = nowAsInstant, updatedAt = nowAsInstant)
      )
      account <- accountRepo.insert(
        Account(
          id = TgIds.UNASSIGNED_UUID,
          firstName = "John",
          lastName = "Doe",
          email = email,
          password = passwordHashed,
          accountTypeId = managerType.id,
          verified = false,
          enabled = true,
          createdAt = nowAsInstant,
          updatedAt = nowAsInstant
        )
      )
    } yield account

  private def createNamedAccount(firstName: String, lastName: String, accountTypeId: UUID): ConnectionIO[Account] =
    accountRepo.insert(
      Account(
        id = TgIds.UNASSIGNED_UUID,
        firstName = firstName,
        lastName = lastName,
        email = uniqueEmail(firstName.toLowerCase),
        password = passwordHashed,
        accountTypeId = accountTypeId,
        verified = true,
        enabled = true,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )
    )

  testNameFromClassName should {

    "auto-create a project mapping when none exists for the target project" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, dumpProject) = projectData
        targetProject <- createSecondProject(dumpProject.organizationId)
        _ <- assign(targetProject.id, account.id)
      } yield (account, dumpProject, targetProject)

      for {
        setupResult <- setup.transact(transactor)
        (account, dumpProject, targetProject) = setupResult
        root <- exportToFolder(transactor, dumpProject.id, account.id)
        importResult <- newImportService(transactor).importProject(targetProject.id, account.id, root.toString)
        mappings <- projectMappingRepo
          .findPaginatedByProjectIdDb(targetProject.id, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        importResult.isRight shouldBe true
        mappings.map(_.projectIdDump) shouldBe List(dumpProject.id)
      }
    }

    "add another project mapping when the target project already maps to a different dump project" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, dumpProject) = projectData
        targetProject <- createSecondProject(dumpProject.organizationId)
        unrelatedDumpProject <- createSecondProject(dumpProject.organizationId)
        _ <- assign(targetProject.id, account.id)
        _ <- insertProjectMapping(targetProject.id, unrelatedDumpProject.id)
      } yield (account, dumpProject, targetProject, unrelatedDumpProject)

      for {
        setupResult <- setup.transact(transactor)
        (account, dumpProject, targetProject, unrelatedDumpProject) = setupResult
        root <- exportToFolder(transactor, dumpProject.id, account.id)
        importResult <- newImportService(transactor).importProject(targetProject.id, account.id, root.toString)
        mappings <- projectMappingRepo
          .findPaginatedByProjectIdDb(targetProject.id, DbPageRequest.default)
          .map(_.items)
          .transact(transactor)
      } yield {
        importResult.isRight shouldBe true
        mappings.map(_.projectIdDump).toSet shouldBe Set(unrelatedDumpProject.id, dumpProject.id)
      }
    }

    "remap the project id and reconcile task statuses by name and description across a mapping" in { transactor =>
      def status(projectId: UUID, name: String, description: String) =
        TaskStatus(UUID.randomUUID(), projectId, name, description, "📌", nowAsInstant, nowAsInstant)

      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, dumpProject) = projectData
        targetProject <- createSecondProject(dumpProject.organizationId)
        _ <- assign(targetProject.id, account.id)
        _ <- insertProjectMapping(targetProject.id, dumpProject.id)
        doneOnDumpSide <- taskStatusRepo.insert(status(dumpProject.id, "Done", "Task completed"))
        blockedOnDumpSide <- taskStatusRepo.insert(status(dumpProject.id, "Blocked", "Task blocked"))
        doneOnTargetSide <- taskStatusRepo.insert(status(targetProject.id, "Done", "Task completed"))
      } yield (account, dumpProject, targetProject, doneOnDumpSide, blockedOnDumpSide, doneOnTargetSide)

      // The tasks only exist as files, as they would in a real cross-database import, so the import
      // inserts them with the remapped project id instead of merging into existing rows.
      val doneTaskId = UUID.randomUUID()
      val blockedTaskId = UUID.randomUUID()

      for {
        setupResult <- setup.transact(transactor)
        (account, dumpProject, targetProject, doneOnDumpSide, blockedOnDumpSide, doneOnTargetSide) = setupResult
        root <- exportToFolder(transactor, dumpProject.id, account.id)
        _ = putEntity(root, DataExportEntityType.Task, doneTaskId, None, handWrittenTask(doneTaskId, dumpProject.id, account.id, doneOnDumpSide.id))
        _ = putEntity(root, DataExportEntityType.Task, blockedTaskId, None, handWrittenTask(blockedTaskId, dumpProject.id, account.id, blockedOnDumpSide.id))
        importResult <- newImportService(transactor).importProject(targetProject.id, account.id, root.toString)
        doneTask <- taskRepo.findById(doneTaskId).transact(transactor)
        blockedTask <- taskRepo.findById(blockedTaskId).transact(transactor)
        doneVersion <- taskVersionRepo.findByTaskId(doneTaskId).transact(transactor).map(_.head)
        blockedVersion <- taskVersionRepo.findByTaskId(blockedTaskId).transact(transactor).map(_.head)
        statuses <- TgPages.readAll(taskStatusRepo.findPaginatedByProjectId(targetProject.id, _)).transact(transactor)
        reimportResult <- newImportService(transactor).importProject(targetProject.id, account.id, root.toString)
        statusesAfterReimport <- TgPages
          .readAll(taskStatusRepo.findPaginatedByProjectId(targetProject.id, _))
          .transact(transactor)
      } yield {
        val cp = new Checkpoint
        cp(importResult.isRight shouldBe true)
        cp(doneTask.projectId shouldBe targetProject.id)
        cp(blockedTask.projectId shouldBe targetProject.id)
        cp(doneVersion.taskStatusId shouldBe doneOnTargetSide.id)
        cp(statuses.count(_.name == "Done") shouldBe 1)
        val newBlocked = statuses.find(_.name == "Blocked").get
        cp(newBlocked.description shouldBe "Task blocked")
        cp(blockedVersion.taskStatusId shouldBe newBlocked.id)
        cp(reimportResult.isRight shouldBe true)
        cp(statusesAfterReimport.size shouldBe statuses.size)
        cp.reportAll()
      }
    }

    "create the exporting account when it does not exist in the target db" in { transactor =>
      val newAccountEmail = uniqueEmail("new")
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        _ <- insertProjectMapping(project.id, project.id)
        developerType <- accountTypeRepo.findByName("developer")
      } yield (account, project, developerType)

      val unknownAccountId = UUID.randomUUID()
      for {
        setupResult <- setup.transact(transactor)
        (account, project, developerType) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        _ = editIndex(root)(_.copy(exportedBy = AccountDumpData(unknownAccountId, "New", "Person", newAccountEmail)))
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        created <- accountRepo.findByEmail(newAccountEmail).transact(transactor)
        mappings <- accountMappingsOf(unknownAccountId).transact(transactor)
      } yield {
        val cp = new Checkpoint
        cp(importResult.isRight shouldBe true)
        cp(created.firstName shouldBe "New")
        cp(created.lastName shouldBe "Person")
        cp(created.verified shouldBe false)
        cp(created.enabled shouldBe true)
        cp(created.accountTypeId shouldBe developerType.id)
        cp(mappings.map(_.accountIdDb) shouldBe List(created.id))
        cp.reportAll()
      }
    }

    "remap account ids in a task and its log entry when the account is found by email under a different id" in {
      transactor =>
        val setup = for {
          projectData <- createExportableProject(newExportPath())
          (account, project) = projectData
          _ <- insertProjectMapping(project.id, project.id)
          status <- createTaskStatus(project.id)
        } yield (account, project, status)

        val exportSideAccountId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val logEntryId = UUID.randomUUID()
        for {
          setupResult <- setup.transact(transactor)
          (account, project, status) = setupResult
          root <- exportToFolder(transactor, project.id, account.id)
          // the same person has another id on the export side, but the email still matches
          _ = editIndex(root)(index => index.copy(exportedBy = index.exportedBy.copy(id = exportSideAccountId)))
          _ = editProjectDump(root)(data =>
            data.copy(referencedAccounts =
              List(AccountDumpData(exportSideAccountId, account.firstName, account.lastName, account.email))
            )
          )
          _ = putEntity(root, DataExportEntityType.Task, taskId, None, handWrittenTask(taskId, project.id, exportSideAccountId, status.id, Some(exportSideAccountId), Some(exportSideAccountId)))
          _ = putEntity(root, DataExportEntityType.LogEntry, logEntryId, None, handWrittenLogEntry(logEntryId, taskId, exportSideAccountId))
          importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
          task <- taskRepo.findById(taskId).transact(transactor)
          version <- taskVersionRepo.findByTaskId(taskId).transact(transactor).map(_.head)
          logEntry <- logEntryRepo.findById(logEntryId).transact(transactor)
          logEntryVersion <- logEntryVersionRepo.findByLogEntryId(logEntryId).transact(transactor).map(_.head)
          mappings <- accountMappingsOf(exportSideAccountId).transact(transactor)
        } yield {
          val cp = new Checkpoint
          cp(importResult.isRight shouldBe true)
          cp(task.createdBy shouldBe account.id)
          cp(version.assignedTo shouldBe Some(account.id))
          cp(version.closedBy shouldBe Some(account.id))
          cp(logEntry.createdBy shouldBe account.id)
          cp(logEntryVersion.createdBy shouldBe account.id)
          cp(mappings.map(_.accountIdDb) shouldBe List(account.id))
          cp.reportAll()
        }
    }

    "reuse an existing account mapping without looking the account up by email" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        _ <- insertProjectMapping(project.id, project.id)
      } yield (account, project)

      val exportSideAccountId = UUID.randomUUID()
      val bogusEmail = uniqueEmail("bogus")
      for {
        setupResult <- setup.transact(transactor)
        (account, project) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        _ <- insertAccountMapping(account.id, exportSideAccountId).transact(transactor)
        _ = editIndex(root)(_.copy(exportedBy = AccountDumpData(exportSideAccountId, "Bogus", "Person", bogusEmail)))
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        bogusLookup <- accountRepo.findByEmail(bogusEmail).transact(transactor).attempt
        mappings <- accountMappingsOf(exportSideAccountId).transact(transactor)
      } yield {
        importResult.isRight shouldBe true
        bogusLookup.isLeft shouldBe true
        mappings.map(_.accountIdDb) shouldBe List(account.id)
      }
    }

    "reject the import when the exporting account is missing and no developer account type exists" in { transactor =>
      val setup = for {
        account <- createAccountWithoutDeveloperType
        orgAndProject <- createOrgAndProject
        (_, project) = orgAndProject
        _ <- assign(project.id, account.id)
        _ <- insertExportConfig(project.id, account.id, newExportPath())
        _ <- insertProjectMapping(project.id, project.id)
      } yield (account, project)

      for {
        setupResult <- setup.transact(transactor)
        (account, project) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        _ = editIndex(root)(_.copy(exportedBy = AccountDumpData(UUID.randomUUID(), "Missing", "Person", uniqueEmail("missing"))))
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
      } yield importResult shouldBe Left(ProjectError.AccountTypeMissing)
    }

    "resolve account mappings for every account a task, its versions and its log entry refer to" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        _ <- insertProjectMapping(project.id, project.id)
        developerType <- accountTypeRepo.findByName("developer")
        creator <- createNamedAccount("Creator", "Person", developerType.id)
        assignee <- createNamedAccount("Assignee", "Person", developerType.id)
        closer <- createNamedAccount("Closer", "Person", developerType.id)
        logAuthor <- createNamedAccount("LogAuthor", "Person", developerType.id)
        status <- createTaskStatus(project.id)
      } yield (account, project, status, creator, assignee, closer, logAuthor)

      val taskId = UUID.randomUUID()
      val logEntryId = UUID.randomUUID()
      for {
        setupResult <- setup.transact(transactor)
        (account, project, status, creator, assignee, closer, logAuthor) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        exportIds = List(creator, assignee, closer, logAuthor).map(a => a -> UUID.randomUUID()).toMap
        _ = editProjectDump(root)(data =>
          data.copy(referencedAccounts = exportIds.toList.map { case (a, exportId) =>
            AccountDumpData(exportId, a.firstName, a.lastName, a.email)
          })
        )
        _ = putEntity(root, DataExportEntityType.Task, taskId, None, handWrittenTask(taskId, project.id, exportIds(creator), status.id, Some(exportIds(assignee)), Some(exportIds(closer))))
        _ = putEntity(root, DataExportEntityType.LogEntry, logEntryId, None, handWrittenLogEntry(logEntryId, taskId, exportIds(logAuthor)))
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        task <- taskRepo.findById(taskId).transact(transactor)
        version <- taskVersionRepo.findByTaskId(taskId).transact(transactor).map(_.head)
        logEntry <- logEntryRepo.findById(logEntryId).transact(transactor)
        logEntryVersion <- logEntryVersionRepo.findByLogEntryId(logEntryId).transact(transactor).map(_.head)
        mappings <- List(creator, assignee, closer, logAuthor)
          .map(a => accountMappingsOf(exportIds(a)).map(a -> _))
          .sequence
          .transact(transactor)
      } yield {
        val cp = new Checkpoint
        cp(importResult.isRight shouldBe true)
        cp(task.createdBy shouldBe creator.id)
        cp(version.assignedTo shouldBe Some(assignee.id))
        cp(version.closedBy shouldBe Some(closer.id))
        cp(logEntry.createdBy shouldBe logAuthor.id)
        cp(logEntryVersion.createdBy shouldBe logAuthor.id)
        mappings.foreach { case (a, found) => cp(found.map(_.accountIdDb) shouldBe List(a.id)) }
        cp.reportAll()
      }
    }

    "reject the whole import when a referenced account is missing and no developer account type exists" in {
      transactor =>
        val setup = for {
          account <- createAccountWithoutDeveloperType
          orgAndProject <- createOrgAndProject
          (_, project) = orgAndProject
          _ <- assign(project.id, account.id)
          _ <- insertExportConfig(project.id, account.id, newExportPath())
          _ <- insertProjectMapping(project.id, project.id)
        } yield (account, project)

        for {
          setupResult <- setup.transact(transactor)
          (account, project) = setupResult
          root <- exportToFolder(transactor, project.id, account.id)
          // the exporting account resolves by email, the referenced one can neither be found nor created
          _ = editProjectDump(root)(
            _.copy(referencedAccounts = List(AccountDumpData(UUID.randomUUID(), "Missing", "Person", uniqueEmail("missing"))))
          )
          importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        } yield importResult shouldBe Left(ProjectError.AccountTypeMissing)
    }

    "create a single mapping for an account that is both the exporter and a referenced account" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        _ <- insertProjectMapping(project.id, project.id)
      } yield (account, project)

      val exportSideAccountId = UUID.randomUUID()
      for {
        setupResult <- setup.transact(transactor)
        (account, project) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        exporter = AccountDumpData(exportSideAccountId, account.firstName, account.lastName, account.email)
        _ = editIndex(root)(_.copy(exportedBy = exporter))
        _ = editProjectDump(root)(_.copy(referencedAccounts = List(exporter)))
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        mappings <- accountMappingsOf(exportSideAccountId).transact(transactor)
      } yield {
        importResult.isRight shouldBe true
        mappings.map(_.accountIdDb) shouldBe List(account.id)
      }
    }

  }

}
