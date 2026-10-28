package com.togger.dump

import com.togger.project.ProjectError
import doobie.implicits._

class DataExportStatusServiceTest extends ExportImportTestSupport {

  private def statusOf(
    result: Either[ProjectError.ProjectError, DataExportStatusResult],
    entityType: DataExportEntityType,
    id: java.util.UUID
  ): DataExportEntityStatus =
    result.toOption.get.pathStatuses.head.entityStatuses.find(s => s.entityType == entityType && s.entityId == id).get

  testNameFromClassName should {

    "report everything as missing from the export before the first export" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (savedAccount, savedProject) = projectData
        label <- createTaskLabel(savedProject.id)
      } yield (savedAccount, savedProject, label)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, label) = setupResult
        result <- newExportService(transactor).checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val statuses = result.toOption.get.pathStatuses.head.entityStatuses
        statuses.forall(_.status == DataExportSyncStatus.MissingFromExport) shouldBe true
        statusOf(result, DataExportEntityType.TaskLabel, label.id).status shouldBe DataExportSyncStatus.MissingFromExport
      }
    }

    "report every entity as in sync right after an export" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (savedAccount, savedProject) = projectData
        status <- createTaskStatus(savedProject.id)
        milestoneId <- createMilestoneWithDate(savedProject.id)
        _ <- createTaskWithFullHistory(savedProject.id, savedAccount.id, status.id, Some(milestoneId))
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        service = newExportService(transactor)
        _ <- service.exportProject(savedProject.id, savedAccount.id)
        result <- service.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        val statuses = result.toOption.get.pathStatuses.head.entityStatuses
        statuses should not be empty
        statuses.map(_.status).distinct shouldBe List(DataExportSyncStatus.InSync)
      }
    }

    "flag an entity out of sync when it changes after the export" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (savedAccount, savedProject) = projectData
        status <- createTaskStatus(savedProject.id)
        taskId <- createTaskWithFullHistory(savedProject.id, savedAccount.id, status.id)
      } yield (savedAccount, savedProject, taskId)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, taskId) = setupResult
        service = newExportService(transactor)
        _ <- service.exportProject(savedProject.id, savedAccount.id)
        latest <- taskVersionRepo.findLatestByTaskId(taskId).transact(transactor)
        _ <- taskVersionRepo.insert(latest.copy(name = "Renamed after export")).transact(transactor)
        result <- service.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        statusOf(result, DataExportEntityType.Task, taskId).status shouldBe DataExportSyncStatus.OutOfSync
        statusOf(result, DataExportEntityType.Task, taskId).differences should not be empty
        val fieldDiffs = statusOf(result, DataExportEntityType.Task, taskId).fieldDiffs
        fieldDiffs.exists(_.database.contains("Renamed after export")) shouldBe true
        fieldDiffs.forall(d => d.disk != d.database) shouldBe true
      }
    }

    "flag entities created or removed after the export" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (savedAccount, savedProject) = projectData
        removed <- createTaskLabel(savedProject.id, "Removed")
      } yield (savedAccount, savedProject, removed)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, removed) = setupResult
        service = newExportService(transactor)
        _ <- service.exportProject(savedProject.id, savedAccount.id)
        _ <- taskLabelRepo.deleteById(removed.id).transact(transactor)
        created <- createTaskLabel(savedProject.id, "Created").transact(transactor)
        result <- service.checkStatus(savedProject.id, savedAccount.id)
      } yield {
        statusOf(result, DataExportEntityType.TaskLabel, removed.id).status shouldBe DataExportSyncStatus.MissingFromDb
        statusOf(result, DataExportEntityType.TaskLabel, created.id).status shouldBe DataExportSyncStatus.MissingFromExport
      }
    }

    "flag an indexed entity whose file was deleted as malformed" in { transactor =>
      val setup = for {
        projectData <- createExportableProject(newExportPath())
        (savedAccount, savedProject) = projectData
        label <- createTaskLabel(savedProject.id)
      } yield (savedAccount, savedProject, label)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, label) = setupResult
        service = newExportService(transactor)
        exported <- service.exportProject(savedProject.id, savedAccount.id)
        root = os.Path(exported.toOption.get.pathResults.head.exportPath)
        _ = os.remove(root / "tasklabels" / s"${label.id}.json")
        result <- service.checkStatus(savedProject.id, savedAccount.id)
      } yield statusOf(result, DataExportEntityType.TaskLabel, label.id).status shouldBe DataExportSyncStatus.Malformed
    }

  }

}
