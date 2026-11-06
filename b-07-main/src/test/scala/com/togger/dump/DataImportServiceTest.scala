package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.deletedentity.DeletedEntity
import com.togger.deletedentity.DeletedEntityType
import com.togger.milestone.MilestoneStepStatus
import com.togger.project.ProjectAccount
import com.togger.project.ProjectError
import com.togger.project.ProjectLabelAssignment
import com.togger.projectlabel.ProjectLabel
import com.togger.projectlabel.ProjectLabelCategory
import com.togger.task.Link
import com.togger.task.LinkVersion
import com.togger.task.TaskLabelAssignment
import com.togger.tasktemplate.TaskTemplate
import com.togger.tasktemplate.TaskTemplateVersion
import com.togger.tgprompttemplate.TgPromptTemplate
import com.togger.tgprompttemplate.TgPromptTemplateVersion
import com.togger.timebox.Timebox
import com.togger.timebox.TimeboxVersion
import doobie.Transactor
import doobie.implicits._
import doobie.postgres.implicits._
import io.circe.generic.auto._

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DataImportServiceTest extends ExportImportTestSupport {

  private case class Fixture(
    accountId: UUID,
    projectId: UUID,
    taskId: UUID,
    milestoneId: UUID,
    templateId: UUID,
    timeboxId: UUID,
    promptId: UUID,
    stepTemplateId: UUID,
    milestoneTemplateId: UUID
  )

  private def clearTombstones(transactor: Transactor[IO], projectId: UUID): IO[Unit] =
    deletedEntityRepo
      .findAllByProjectId(projectId)
      .flatMap(_.traverse_(t => deletedEntityRepo.deleteByEntity(t.projectId, t.entityType, t.entityId)))
      .transact(transactor)

  private def taskResults(result: Either[_, DataImportResult]): List[DataImportStatus] =
    result.toOption.get.results.filter(_.entityType == DataExportEntityType.Task).map(_.status)

  private def createFullProject(exportPath: os.Path) =
    for {
      projectData <- createExportableProject(exportPath)
      (account, savedProject) = projectData
      status <- createTaskStatus(savedProject.id)
      label <- createTaskLabel(savedProject.id)
      milestoneId <- createMilestoneWithDate(savedProject.id)
      taskId <- createTaskWithFullHistory(savedProject.id, account.id, status.id, Some(milestoneId))
      _ <- taskLabelAssignmentRepo.insert(TaskLabelAssignment(taskId, label.id, nowAsInstant))
      template <- taskTemplateRepo.insert(
        TaskTemplate(UUID.randomUUID(), savedProject.id, Some(label.id), account.id, nowAsInstant)
      )
      _ <- taskTemplateVersionRepo.insert(
        TaskTemplateVersion(UUID.randomUUID(), template.id, "Template", "Template body", nowAsInstant)
      )
      prompt <- tgPromptTemplateRepo.insert(TgPromptTemplate(UUID.randomUUID(), account.id, nowAsInstant))
      _ <- tgPromptTemplateVersionRepo.insert(
        TgPromptTemplateVersion(UUID.randomUUID(), prompt.id, "Prompt", "Prompt text", nowAsInstant)
      )
      stepTemplateId <- createMilestoneStepTemplate(savedProject.id, account.id, "Design", "Design description")
      milestoneTemplateId <- createMilestoneTemplate(
        savedProject.id,
        account.id,
        "New feature",
        "Feature description",
        List(List(stepTemplateId), List(stepTemplateId))
      )
      timebox <- timeboxRepo.insert(Timebox(UUID.randomUUID(), savedProject.id, nowAsInstant))
      timeboxVersion <- timeboxVersionRepo.insert(
        TimeboxVersion(
          UUID.randomUUID(),
          timebox.id,
          LocalDate.now(),
          LocalDate.now().plusDays(14),
          Some("Sprint"),
          nowAsInstant,
          "Created"
        )
      )
      taskVersion <- taskVersionRepo.findLatestByTaskId(taskId)
      _ <- timeboxVersionRepo.insertPlannedTasks(timeboxVersion.version, List(taskVersion.version))
    } yield Fixture(
      account.id,
      savedProject.id,
      taskId,
      milestoneId,
      template.id,
      timebox.id,
      prompt.id,
      stepTemplateId,
      milestoneTemplateId
    )

  private def resultOf(result: DataImportResult, entityType: DataExportEntityType): List[DataImportEntityResult] =
    result.results.filter(_.entityType == entityType)

  testNameFromClassName should {

    "reject an import for an account not assigned to the project" in { transactor =>
      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- newImportService(transactor).importProject(savedProject.id, savedAccount.id, newExportPath().toString)
      } yield result shouldBe Left(ProjectError.NotAssignedToProject)
    }

    "reject an import from a folder without an index" in { transactor =>
      val emptyFolder = newExportPath()
      os.makeDir.all(emptyFolder)
      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(ProjectAccount(savedProject.id, savedAccount.id, nowAsInstant))
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        result <- newImportService(transactor).importProject(savedProject.id, savedAccount.id, emptyFolder.toString)
      } yield result shouldBe Left(ProjectError.DumpManifestMissing)
    }

    "restore every entity type from the tree and be idempotent on a second import" in { transactor =>
      val exportPath = newExportPath()
      for {
        fixture <- createFullProject(exportPath).transact(transactor)
        exported <- newExportService(transactor).exportProject(fixture.projectId, fixture.accountId)
        root = exported.toOption.get.pathResults.head.exportPath
        _ <- deleteProjectContent.transact(transactor)
        first <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root)
        second <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root)
        restoredTask <- taskRepo.findById(fixture.taskId).transact(transactor)
        restoredLabels <- taskLabelAssignmentRepo.findLabelsByTaskId(fixture.taskId).transact(transactor)
        restoredMilestone <- milestoneRepo.findById(fixture.milestoneId).transact(transactor)
        restoredTemplate <- taskTemplateRepo.findFullById(fixture.templateId).transact(transactor)
        restoredSteps <- milestoneStepVersionRepo.findLatestByMilestoneId(fixture.milestoneId).transact(transactor)
        restoredTaskVersion <- taskVersionRepo.findLatestByTaskId(fixture.taskId).transact(transactor)
        restoredStepTemplate <- milestoneStepTemplateRepo.findFullById(fixture.stepTemplateId).transact(transactor)
        restoredMilestoneTemplate <- milestoneTemplateRepo
          .findFullById(fixture.milestoneTemplateId)
          .transact(transactor)
        restoredTimebox <- timeboxRepo.findById(fixture.timeboxId).transact(transactor)
        restoredPrompt <- tgPromptTemplateRepo.findFullById(fixture.promptId).transact(transactor)
        restoredPlanned <- taskVersionRepo
          .findLatestByTaskId(fixture.taskId)
          .flatMap(v =>
            timeboxVersionRepo
              .findLatestByTimeboxId(fixture.timeboxId)
              .flatMap(tv => timeboxVersionRepo.findPlannedTasks(tv.version).map(_.map(_.version) -> v.version))
          )
          .transact(transactor)
      } yield {
        val cp = new Checkpoint
        val imported = first.toOption.get
        List(
          DataExportEntityType.Task,
          DataExportEntityType.LogEntry,
          DataExportEntityType.Milestone,
          DataExportEntityType.TaskTemplate,
          DataExportEntityType.MilestoneStepTemplate,
          DataExportEntityType.MilestoneTemplate,
          DataExportEntityType.Timebox,
          DataExportEntityType.TgPromptTemplate,
          DataExportEntityType.TaskStatus,
          DataExportEntityType.TaskLabel
        ).foreach { entityType =>
          cp(resultOf(imported, entityType).map(_.status).distinct shouldBe List(DataImportStatus.Inserted))
        }
        cp(imported.results.filter(_.error.isDefined) shouldBe empty)
        cp(restoredTask.projectId shouldBe fixture.projectId)
        cp(restoredLabels.map(_.name) shouldBe List("Label"))
        cp(restoredMilestone.id shouldBe fixture.milestoneId)
        cp(restoredTemplate.taskLabelName shouldBe Some("Label"))
        cp(restoredSteps.map(s => (s.name, s.status)) shouldBe List(("Milestone step", MilestoneStepStatus.InProgress)))
        cp(restoredStepTemplate.latestVersion.name shouldBe "Design")
        cp(restoredTaskVersion.milestoneStepId shouldBe restoredSteps.headOption.map(_.milestoneStepId))
        cp(restoredTaskVersion.milestoneStepId.isDefined shouldBe true)
        cp(restoredMilestoneTemplate.latest.version.name shouldBe "New feature")
        cp(
          restoredMilestoneTemplate.latest.steps.map(_.stepTemplateId) shouldBe List(
            fixture.stepTemplateId,
            fixture.stepTemplateId
          )
        )
        cp(restoredTimebox.id shouldBe fixture.timeboxId)
        cp(restoredPrompt.tgPromptTemplate.createdBy shouldBe fixture.accountId)
        cp(restoredPrompt.latestVersion.prompt shouldBe "Prompt text")
        cp(restoredPlanned._1 shouldBe List(restoredPlanned._2))

        cp(second.toOption.get.results.map(_.status).distinct shouldBe List(DataImportStatus.Unchanged))
        cp.reportAll()
      }
    }

    "export only the prompts of the exporting account" in { transactor =>
      val exportPath = newExportPath()
      for {
        fixture <- createFullProject(exportPath).transact(transactor)
        owner <- accountRepo.findById(fixture.accountId).transact(transactor)
        other <- accountRepo
          .insert(createAccount(owner.accountTypeId).copy(email = "other@email.com"))
          .transact(transactor)
        foreign <- tgPromptTemplateRepo
          .insert(TgPromptTemplate(UUID.randomUUID(), other.id, nowAsInstant))
          .transact(transactor)
        _ <- tgPromptTemplateVersionRepo
          .insert(TgPromptTemplateVersion(UUID.randomUUID(), foreign.id, "Foreign", "Foreign text", nowAsInstant))
          .transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        files = os.list(root / "prompts").map(_.baseName).toList
      } yield files shouldBe List(fixture.promptId.toString)
    }

    "insert a task, its link and its log entry that exist in the tree but not in the db" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        status <- createTaskStatus(project.id)
      } yield (account, project, status)

      val taskId = UUID.randomUUID()
      val linkId = UUID.randomUUID()
      val logEntryId = UUID.randomUUID()
      val linkData = LinkDumpData(
        Link(linkId, taskId, nowAsInstant),
        List(LinkVersion(UUID.randomUUID(), linkId, "Disk link", "https://example.com", nowAsInstant, nowAsInstant))
      )

      for {
        setupResult <- setup.transact(transactor)
        (account, project, status) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        task = handWrittenTask(taskId, project.id, account.id, status.id).copy(links = List(linkData))
        _ = putEntity(root, DataExportEntityType.Task, taskId, None, task)
        _ = putEntity(
          root,
          DataExportEntityType.LogEntry,
          logEntryId,
          None,
          handWrittenLogEntry(logEntryId, taskId, account.id)
        )
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        persistedTask <- taskRepo.findById(taskId).transact(transactor)
        versions <- taskVersionRepo.findByTaskId(taskId).transact(transactor)
        links <- linkRepo.findByTaskId(taskId).transact(transactor)
        logEntries <- logEntryRepo.findByTaskId(taskId).transact(transactor)
      } yield {
        val cp = new Checkpoint
        val imported = importResult.toOption.get
        val taskResult = resultOf(imported, DataExportEntityType.Task).find(_.entityId == taskId).get
        val logEntryResult = resultOf(imported, DataExportEntityType.LogEntry).find(_.entityId == logEntryId).get
        cp(taskResult.status shouldBe DataImportStatus.Inserted)
        cp(taskResult.versionsAdded shouldBe 2) // the task version and the link version
        cp(taskResult.childrenAdded shouldBe 1) // the link
        cp(logEntryResult.status shouldBe DataImportStatus.Inserted)
        cp(logEntryResult.versionsAdded shouldBe 1)
        cp(persistedTask.id shouldBe taskId)
        cp(versions.size shouldBe 1)
        cp(links.size shouldBe 1)
        cp(logEntries.size shouldBe 1)
        cp.reportAll()
      }
    }

    "merge missing versions and links into a task that already exists in the db, and be idempotent on reimport" in {
      transactor =>
        val setup = for {
          projectData <- createExportableProject(newExportPath())
          (account, project) = projectData
          status <- createTaskStatus(project.id)
          taskId <- createTaskWithFullHistory(project.id, account.id, status.id)
        } yield (account, project, taskId)

        for {
          setupResult <- setup.transact(transactor)
          (account, project, taskId) = setupResult
          root <- exportToFolder(transactor, project.id, account.id)
          // a local db that is behind the export: no older task version, no link, no older log entry version
          taskVersions <- taskVersionRepo.findByTaskId(taskId).transact(transactor)
          link <- linkRepo.findByTaskId(taskId).transact(transactor).map(_.head)
          logEntry <- logEntryRepo.findByTaskId(taskId).transact(transactor).map(_.head)
          logEntryVersions <- logEntryVersionRepo.findByLogEntryId(logEntry.id).transact(transactor)
          _ <- taskVersionRepo.deleteByVersion(taskVersions.map(_.version).min).transact(transactor)
          _ <- linkRepo.deleteById(link.id).transact(transactor)
          _ <- logEntryVersionRepo.deleteByVersion(logEntryVersions.map(_.version).min).transact(transactor)
          importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
          restoredVersions <- taskVersionRepo.findByTaskId(taskId).transact(transactor)
          restoredLinks <- linkRepo.findByTaskId(taskId).transact(transactor)
          restoredLogEntryVersions <- logEntryVersionRepo.findByLogEntryId(logEntry.id).transact(transactor)
          reimportResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
        } yield {
          val cp = new Checkpoint
          val imported = importResult.toOption.get
          val taskResult = resultOf(imported, DataExportEntityType.Task).find(_.entityId == taskId).get
          val logEntryResult = resultOf(imported, DataExportEntityType.LogEntry).find(_.entityId == logEntry.id).get
          cp(taskResult.status shouldBe DataImportStatus.Merged)
          cp(taskResult.versionsAdded shouldBe 3) // 1 task version + 2 link versions of the re-inserted link
          cp(taskResult.childrenAdded shouldBe 1) // the link
          cp(logEntryResult.status shouldBe DataImportStatus.Merged)
          cp(logEntryResult.versionsAdded shouldBe 1)
          cp(restoredVersions.size shouldBe 2)
          cp(restoredLinks.size shouldBe 1)
          cp(restoredLogEntryVersions.size shouldBe 2)

          val reimported = reimportResult.toOption.get
          cp(reimported.results.map(_.status).distinct shouldBe List(DataImportStatus.Unchanged))
          cp(reimported.results.map(_.versionsAdded).sum shouldBe 0)
          cp(reimported.results.map(_.childrenAdded).sum shouldBe 0)
          cp.reportAll()
        }
    }

    "create a new project with its members and tasks from an exported tree" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- deleteProjectContent.transact(transactor) // the tree comes from another database
        source <- projectRepo.findById(fixture.projectId).transact(transactor)
        imported <- newImportService(transactor).importNewProject(
          source.organizationId,
          fixture.accountId,
          root.toString
        )
        (newProjectId, result) = imported.toOption.get
        created <- projectRepo.findById(newProjectId).transact(transactor)
        isMember <- projectAccountRepo.isMember(newProjectId, fixture.accountId).transact(transactor)
        tasks <- taskRepo.findAllByProjectId(newProjectId).transact(transactor)
      } yield {
        newProjectId should not be fixture.projectId
        created.organizationId shouldBe source.organizationId
        created.name shouldBe source.name
        isMember shouldBe true
        tasks.map(_.id) should contain(fixture.taskId)
        result.results.filter(_.entityType == DataExportEntityType.Task).map(_.status) shouldBe List(
          DataImportStatus.Inserted
        )
      }
    }

    "apply the project fields of a newer tree to the project, and leave a newer project alone" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        exported <- projectRepo.findById(fixture.projectId).transact(transactor)
        older = exported.copy(icon = "old-icon", description = "old description")
        _ <- projectRepo.update(older, exported.updatedAt.minusSeconds(60)).transact(transactor)
        first <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        restored <- projectRepo.findById(fixture.projectId).transact(transactor)
        newer = restored.copy(icon = "newer-icon")
        _ <- projectRepo.update(newer, exported.updatedAt.plusSeconds(60)).transact(transactor)
        second <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        kept <- projectRepo.findById(fixture.projectId).transact(transactor)
      } yield {
        restored.icon shouldBe exported.icon
        restored.description shouldBe exported.description
        restored.updatedAt shouldBe exported.updatedAt
        first.toOption.get.results.filter(_.entityType == DataExportEntityType.Project).map(_.status) shouldBe List(
          DataImportStatus.Merged
        )
        kept.icon shouldBe "newer-icon"
        second.toOption.get.results.filter(_.entityType == DataExportEntityType.Project).map(_.status) shouldBe List(
          DataImportStatus.Unchanged
        )
      }
    }

    "report a member the database lacks as importable, and a member only the database has as in sync" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        owner <- accountRepo.findById(fixture.accountId).transact(transactor)
        other <- accountRepo
          .insert(createAccount(owner.accountTypeId).copy(email = "member@email.com"))
          .transact(transactor)
        service = newExportService(transactor)
        check = (root: os.Path) =>
          service
            .checkImportStatus(fixture.projectId, fixture.accountId, root.toString)
            .map(_.toOption.get.entityStatuses.filter(_.entityType == DataExportEntityType.Members).map(_.status))
        _ <- projectAccountRepo
          .insert(ProjectAccount(fixture.projectId, other.id, nowAsInstant))
          .transact(transactor)
        withMember <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- projectAccountRepo.deleteById(fixture.projectId, other.id).transact(transactor)
        lacking <- check(withMember)
        _ <- projectAccountRepo
          .insert(ProjectAccount(fixture.projectId, other.id, nowAsInstant))
          .transact(transactor)
        withoutMember <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- IO(editMembers(withoutMember, _ => List(fixture.accountId)))
        databaseOnly <- check(withoutMember)
      } yield {
        lacking shouldBe List(DataExportSyncStatus.OutOfSync)
        databaseOnly shouldBe List(DataExportSyncStatus.InSync)
      }
    }

    "compare the members of a tree with the ones of the project it was imported into" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- deleteProjectContent.transact(transactor) // the tree comes from another database
        source <- projectRepo.findById(fixture.projectId).transact(transactor)
        imported <- newImportService(transactor).importNewProject(
          source.organizationId,
          fixture.accountId,
          root.toString
        )
        (newProjectId, _) = imported.toOption.get
        status <- newExportService(transactor).checkImportStatus(newProjectId, fixture.accountId, root.toString)
      } yield status.toOption.get.entityStatuses
        .filter(_.entityType == DataExportEntityType.Members)
        .map(_.status) shouldBe List(DataExportSyncStatus.InSync)
    }

    "compare the project of a tree with the new project it was imported into" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- deleteProjectContent.transact(transactor) // the tree comes from another database
        source <- projectRepo.findById(fixture.projectId).transact(transactor)
        imported <- newImportService(transactor).importNewProject(
          source.organizationId,
          fixture.accountId,
          root.toString
        )
        (newProjectId, _) = imported.toOption.get
        status <- newExportService(transactor).checkImportStatus(newProjectId, fixture.accountId, root.toString)
      } yield status.toOption.get.entityStatuses
        .filter(_.entityType == DataExportEntityType.Project)
        .map(s => (s.status, s.fieldDiffs.map(_.field))) shouldBe List((DataExportSyncStatus.InSync, Nil))
    }

    "compare the members of a tree by email, whatever the names of the accounts" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        // the same email belongs to an account with another name in the database the tree is checked against
        _ <-
          sql"update account set first_name = 'Renamed', last_name = 'Person' where id = ${fixture.accountId}".update.run
            .transact(transactor)
        status <- newExportService(transactor).checkImportStatus(fixture.projectId, fixture.accountId, root.toString)
      } yield status.toOption.get.entityStatuses
        .filter(_.entityType == DataExportEntityType.Members)
        .map(s => (s.status, s.fieldDiffs.map(_.field))) shouldBe List((DataExportSyncStatus.InSync, Nil))
    }

    "compare the project labels of a tree with the ones of the project by name, whatever their ids" in { transactor =>
      def assignLabel(projectId: UUID, name: String) =
        for {
          category <- projectLabelCategoryRepo.insert(
            ProjectLabelCategory(UUID.randomUUID(), s"Area $name", nowAsInstant, nowAsInstant)
          )
          label <- projectLabelRepo.insert(
            ProjectLabel(UUID.randomUUID(), name, "description", category.id, nowAsInstant, nowAsInstant)
          )
          _ <- projectLabelAssignmentRepo.insert(ProjectLabelAssignment(projectId, label.id, nowAsInstant))
        } yield label

      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        original <- assignLabel(fixture.projectId, "Scala").transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        // the tree comes from another database, where the label of the same name has another id
        _ <- (for {
          _ <- projectLabelAssignmentRepo.deleteAllByProjectId(fixture.projectId)
          _ <- projectLabelRepo.deleteById(original.id)
          _ <- projectLabelCategoryRepo.deleteById(original.categoryId)
          _ <- assignLabel(fixture.projectId, "Scala")
        } yield ()).transact(transactor)
        status <- newExportService(transactor).checkImportStatus(fixture.projectId, fixture.accountId, root.toString)
      } yield {
        val statuses = status.toOption.get.entityStatuses
        statuses
          .filter(s =>
            s.entityType == DataExportEntityType.ProjectLabel || s.entityType == DataExportEntityType.Project
          )
          .map(s => (s.entityType, s.status, s.fieldDiffs.map(_.field))) should contain theSameElementsAs List(
          (DataExportEntityType.ProjectLabel, DataExportSyncStatus.InSync, Nil),
          (DataExportEntityType.Project, DataExportSyncStatus.InSync, Nil)
        )
      }
    }

    "delete a task the tree deleted, and record the deletion so it is carried on" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        older = newExportPath()
        _ = os.copy(root, older)
        _ <- newTaskService(transactor).deleteTask(fixture.taskId)
        _ <- exportToFolder(transactor, fixture.projectId, fixture.accountId) // the tree now holds the tombstone
        _ <- clearTombstones(transactor, fixture.projectId) // another database that still has the task
        restored <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, older.toString)
        imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        task <- taskRepo.findOptionById(fixture.taskId).transact(transactor)
        tombstones <- deletedEntityRepo.findAllByProjectId(fixture.projectId).transact(transactor)
      } yield {
        taskResults(restored) shouldBe List(DataImportStatus.Inserted)
        taskResults(imported) shouldBe List(DataImportStatus.Deleted)
        task shouldBe None
        tombstones.map(t => (t.entityType, t.entityId)).toSet should contain((DeletedEntityType.Task, fixture.taskId))
      }
    }

    "delete a log entry the tree deleted, and keep its task" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        older = newExportPath()
        _ = os.copy(root, older)
        logEntries <- logEntryRepo.findByTaskId(fixture.taskId).transact(transactor)
        logEntryId = logEntries.head.id
        _ <- newLogEntryService(transactor).deleteLogEntry(logEntryId)
        _ <- exportToFolder(transactor, fixture.projectId, fixture.accountId) // the tree now holds the tombstone
        _ <- clearTombstones(transactor, fixture.projectId) // another database that still has the log entry
        restored <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, older.toString)
        imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        remaining <- logEntryRepo.findByTaskId(fixture.taskId).transact(transactor)
        task <- taskRepo.findOptionById(fixture.taskId).transact(transactor)
      } yield {
        restored.toOption.get.results
          .filter(_.entityType == DataExportEntityType.LogEntry)
          .map(_.status) shouldBe List(DataImportStatus.Inserted)
        imported.toOption.get.results
          .filter(_.entityType == DataExportEntityType.LogEntry)
          .map(_.status) shouldBe List(DataImportStatus.Deleted)
        remaining shouldBe empty
        task should not be None
      }
    }

    "keep a task that changed after the tree deleted it" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        older = newExportPath()
        _ = os.copy(root, older)
        _ <- newTaskService(transactor).deleteTask(fixture.taskId)
        _ <- exportToFolder(transactor, fixture.projectId, fixture.accountId) // the tree now holds the tombstone
        _ = editIndex(root)(index =>
          index.copy(deleted = index.deleted.view.mapValues(_.view.mapValues(_ => Instant.EPOCH).toMap).toMap)
        ) // deleted long before the task was last changed
        _ <- clearTombstones(transactor, fixture.projectId) // another database that still has the task
        _ <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, older.toString)
        imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        task <- taskRepo.findOptionById(fixture.taskId).transact(transactor)
      } yield {
        taskResults(imported) shouldBe List(DataImportStatus.Skipped)
        task should not be None
      }
    }

    "not bring back a task that the project deleted, from an older tree" in { transactor =>
      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- newTaskService(transactor).deleteTask(fixture.taskId)
        imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        task <- taskRepo.findOptionById(fixture.taskId).transact(transactor)
        logEntries <- logEntryRepo.findAllByProjectId(fixture.projectId).transact(transactor)
      } yield {
        taskResults(imported) shouldBe List(DataImportStatus.Skipped)
        imported.toOption.get.results
          .filter(_.entityType == DataExportEntityType.LogEntry)
          .map(_.status)
          .distinct shouldBe List(DataImportStatus.Skipped)
        task shouldBe None
        logEntries shouldBe empty
      }
    }

    "bring a task back that changed in the tree after the project deleted it, and clear the tombstone" in {
      transactor =>
        for {
          fixture <- createFullProject(newExportPath()).transact(transactor)
          root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
          _ <- taskRepo.deleteById(fixture.taskId).transact(transactor)
          _ <- deletedEntityRepo
            .insertAll(
              List(DeletedEntity(fixture.projectId, DeletedEntityType.Task, fixture.taskId, Instant.EPOCH))
            )
            .transact(transactor)
          imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
          task <- taskRepo.findOptionById(fixture.taskId).transact(transactor)
          tombstones <- deletedEntityRepo.findAllByProjectId(fixture.projectId).transact(transactor)
        } yield {
          taskResults(imported) shouldBe List(DataImportStatus.Inserted)
          task should not be None
          tombstones.map(_.entityId) should not contain fixture.taskId
        }
    }

    "reject a new project import from a folder without an index" in { transactor =>
      val emptyFolder = newExportPath()
      os.makeDir.all(emptyFolder)
      for {
        account <- createAccount.transact(transactor)
        orgAndProject <- createOrgAndProject.transact(transactor)
        result <- newImportService(transactor).importNewProject(orgAndProject._1.id, account.id, emptyFolder.toString)
      } yield result shouldBe Left(ProjectError.DumpManifestMissing)
    }

    "merge a template into the one its label already has in the target project" in { transactor =>
      val setupTarget = (sourceProjectId: UUID, accountId: UUID) =>
        for {
          sourceProject <- projectRepo.findById(sourceProjectId)
          targetProject <- createSecondProject(sourceProject.organizationId)
          _ <- projectAccountRepo.insert(ProjectAccount(targetProject.id, accountId, nowAsInstant))
          label <- createTaskLabel(targetProject.id)
          defaultTemplate <- taskTemplateRepo.insert(
            TaskTemplate(UUID.randomUUID(), targetProject.id, Some(label.id), accountId, nowAsInstant)
          )
          _ <- taskTemplateVersionRepo.insert(
            TaskTemplateVersion(UUID.randomUUID(), defaultTemplate.id, "Default", "Default body", nowAsInstant)
          )
        } yield (targetProject, label, defaultTemplate)

      for {
        fixture <- createFullProject(newExportPath()).transact(transactor)
        root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
        _ <- deleteProjectContent.transact(transactor) // the tree comes from another database
        target <- setupTarget(fixture.projectId, fixture.accountId).transact(transactor)
        (targetProject, targetLabel, defaultTemplate) = target
        first <- newImportService(transactor).importProject(targetProject.id, fixture.accountId, root.toString)
        second <- newImportService(transactor).importProject(targetProject.id, fixture.accountId, root.toString)
        merged <- taskTemplateRepo.findFullByLabelId(targetLabel.id).transact(transactor)
        versions <- taskTemplateVersionRepo
          .findPaginatedByTemplateId(defaultTemplate.id, com.togger.common.DbPageRequest.default)
          .transact(transactor)
      } yield {
        val cp = new Checkpoint
        val templateResult = resultOf(first.toOption.get, DataExportEntityType.TaskTemplate).head
        cp(first.toOption.get.results.filter(_.error.isDefined) shouldBe empty)
        cp(templateResult.status shouldBe DataImportStatus.Merged)
        cp(templateResult.versionsAdded shouldBe 1)
        cp(merged.map(_.template.id) shouldBe Some(defaultTemplate.id))
        cp(versions.items.map(_.name).toSet shouldBe Set("Default", "Template"))
        cp(
          resultOf(second.toOption.get, DataExportEntityType.TaskTemplate).map(_.status) shouldBe List(
            DataImportStatus.Unchanged
          )
        )
        cp.reportAll()
      }
    }

    "restore the steps that run in parallel with the same position" in { transactor =>
      val exportPath = newExportPath()
      for {
        fixture <- createFullProject(exportPath).transact(transactor)
        _ <- (for {
          _ <- createMilestoneStepAt(fixture.milestoneId, "Backend", 1)
          _ <- createMilestoneStepAt(fixture.milestoneId, "Frontend", 1)
        } yield ()).transact(transactor)
        exported <- newExportService(transactor).exportProject(fixture.projectId, fixture.accountId)
        root = exported.toOption.get.pathResults.head.exportPath
        _ <- deleteProjectContent.transact(transactor)
        imported <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root)
        restoredSteps <- milestoneStepVersionRepo.findLatestByMilestoneId(fixture.milestoneId).transact(transactor)
      } yield {
        imported.toOption.get.results.filter(_.error.isDefined) shouldBe empty
        restoredSteps.map(s => s.name -> s.position).toSet shouldBe
          Set("Milestone step" -> 0, "Backend" -> 1, "Frontend" -> 1)
      }
    }

    "merge step templates and milestone templates into the ones of the same name the target project has" in {
      transactor =>
        val setupTarget = (sourceProjectId: UUID, accountId: UUID) =>
          for {
            sourceProject <- projectRepo.findById(sourceProjectId)
            targetProject <- createSecondProject(sourceProject.organizationId)
            _ <- projectAccountRepo.insert(ProjectAccount(targetProject.id, accountId, nowAsInstant))
            design <- createMilestoneStepTemplate(targetProject.id, accountId, "Design", "The default design step")
            featureId <- createMilestoneTemplate(
              targetProject.id,
              accountId,
              "New feature",
              "The default",
              List(List(design))
            )
          } yield (targetProject, design, featureId)

        for {
          fixture <- createFullProject(newExportPath()).transact(transactor)
          root <- exportToFolder(transactor, fixture.projectId, fixture.accountId)
          _ <- deleteProjectContent.transact(transactor) // the tree comes from another database
          target <- setupTarget(fixture.projectId, fixture.accountId).transact(transactor)
          (targetProject, targetDesign, targetFeature) = target
          first <- newImportService(transactor).importProject(targetProject.id, fixture.accountId, root.toString)
          second <- newImportService(transactor).importProject(targetProject.id, fixture.accountId, root.toString)
          stepTemplates <- milestoneStepTemplateRepo
            .findPaginatedFullByProjectId(targetProject.id, com.togger.common.DbPageRequest.default)
            .transact(transactor)
          milestoneTemplates <- milestoneTemplateRepo
            .findPaginatedFullByProjectId(targetProject.id, com.togger.common.DbPageRequest.default)
            .transact(transactor)
          featureVersions <- milestoneTemplateVersionRepo
            .findPaginatedByTemplateId(targetFeature, com.togger.common.DbPageRequest.default)
            .transact(transactor)
          featureSteps <- milestoneTemplateVersionRepo
            .findStepsByVersions(featureVersions.items.map(_.version))
            .transact(transactor)
        } yield {
          val cp = new Checkpoint
          val firstResults = first.toOption.get
          cp(firstResults.results.filter(_.error.isDefined) shouldBe empty)
          cp(
            resultOf(firstResults, DataExportEntityType.MilestoneStepTemplate).map(_.status) shouldBe List(
              DataImportStatus.Merged
            )
          )
          cp(
            resultOf(firstResults, DataExportEntityType.MilestoneTemplate).map(_.status) shouldBe List(
              DataImportStatus.Merged
            )
          )
          cp(stepTemplates.items.map(_.template.id) shouldBe List(targetDesign))
          cp(milestoneTemplates.items.map(_.template.id) shouldBe List(targetFeature))
          cp(featureVersions.items.map(_.description).toSet shouldBe Set("The default", "Feature description"))
          // The steps of the imported version point at the step template of the target, not at the one of the tree.
          cp(featureSteps.values.flatten.map(_.stepTemplateId).toSet shouldBe Set(targetDesign))
          cp(
            resultOf(second.toOption.get, DataExportEntityType.MilestoneStepTemplate).map(_.status) shouldBe List(
              DataImportStatus.Unchanged
            )
          )
          cp(
            resultOf(second.toOption.get, DataExportEntityType.MilestoneTemplate).map(_.status) shouldBe List(
              DataImportStatus.Unchanged
            )
          )
          cp.reportAll()
        }
    }

    "report a task with nothing new in the tree as unchanged" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (account, project) = projectData
        status <- createTaskStatus(project.id)
        taskId <- createTaskWithFullHistory(project.id, account.id, status.id)
      } yield (account, project, taskId)

      for {
        setupResult <- setup.transact(transactor)
        (account, project, taskId) = setupResult
        root <- exportToFolder(transactor, project.id, account.id)
        importResult <- newImportService(transactor).importProject(project.id, account.id, root.toString)
      } yield {
        val taskResult = resultOf(importResult.toOption.get, DataExportEntityType.Task).find(_.entityId == taskId).get
        taskResult.status shouldBe DataImportStatus.Unchanged
        taskResult.versionsAdded shouldBe 0
        taskResult.childrenAdded shouldBe 0
      }
    }

    "report a tampered file as malformed without failing the rest of the import" in { transactor =>
      val exportPath = newExportPath()
      for {
        fixture <- createFullProject(exportPath).transact(transactor)
        exported <- newExportService(transactor).exportProject(fixture.projectId, fixture.accountId)
        root = os.Path(exported.toOption.get.pathResults.head.exportPath)
        _ = os.write.over(root / "tasks" / s"${fixture.taskId}.json", "{}")
        _ <- deleteProjectContent.transact(transactor)
        result <- newImportService(transactor).importProject(fixture.projectId, fixture.accountId, root.toString)
        milestoneRestored <- milestoneRepo.findById(fixture.milestoneId).transact(transactor).attempt
      } yield {
        val imported = result.toOption.get
        resultOf(imported, DataExportEntityType.Task).map(_.status) shouldBe List(DataImportStatus.Malformed)
        resultOf(imported, DataExportEntityType.Milestone).map(_.status) shouldBe List(DataImportStatus.Inserted)
        milestoneRestored.isRight shouldBe true
      }
    }

  }

}
