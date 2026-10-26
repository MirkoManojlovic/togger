package com.togger.dump

import com.togger.project.{ProjectAccount, ProjectError}
import com.togger.task.{Link, LinkVersion, TaskLabelAssignment}
import com.togger.tasktemplate.{TaskTemplate, TaskTemplateVersion}
import com.togger.timebox.{Timebox, TimeboxVersion}
import doobie.implicits._
import io.circe.generic.auto._

import java.time.LocalDate
import java.util.UUID

class DataImportServiceTest extends ExportImportTestSupport {

  private case class Fixture(
    accountId: UUID,
    projectId: UUID,
    taskId: UUID,
    milestoneId: UUID,
    templateId: UUID,
    timeboxId: UUID
  )

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
    } yield Fixture(account.id, savedProject.id, taskId, milestoneId, template.id, timebox.id)

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
        restoredTimebox <- timeboxRepo.findById(fixture.timeboxId).transact(transactor)
        restoredPlanned <- taskVersionRepo
          .findLatestByTaskId(fixture.taskId)
          .flatMap(v => timeboxVersionRepo.findLatestByTimeboxId(fixture.timeboxId).flatMap(tv => timeboxVersionRepo.findPlannedTasks(tv.version).map(_.map(_.version) -> v.version)))
          .transact(transactor)
      } yield {
        val cp = new Checkpoint
        val imported = first.toOption.get
        List(
          DataExportEntityType.Task,
          DataExportEntityType.LogEntry,
          DataExportEntityType.Milestone,
          DataExportEntityType.TaskTemplate,
          DataExportEntityType.Timebox,
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
        cp(restoredTimebox.id shouldBe fixture.timeboxId)
        cp(restoredPlanned._1 shouldBe List(restoredPlanned._2))

        cp(second.toOption.get.results.map(_.status).distinct shouldBe List(DataImportStatus.Unchanged))
        cp.reportAll()
      }
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
        _ = putEntity(root, DataExportEntityType.LogEntry, logEntryId, None, handWrittenLogEntry(logEntryId, taskId, account.id))
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
        cp(resultOf(second.toOption.get, DataExportEntityType.TaskTemplate).map(_.status) shouldBe List(DataImportStatus.Unchanged))
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
