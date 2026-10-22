package com.togger.dump

import com.togger.project.{ProjectAccount, ProjectError}
import com.togger.task.TaskLabelAssignment
import com.togger.tasktemplate.{TaskTemplate, TaskTemplateVersion}
import com.togger.timebox.{Timebox, TimeboxVersion}
import doobie.implicits._

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
