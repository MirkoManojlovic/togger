package com.togger.dump

import com.togger.project.{ProjectAccount, ProjectError}
import doobie.implicits._

class DataExportServiceTest extends ExportImportTestSupport {

  testNameFromClassName should {

    "reject an export for an account not assigned to the project" in { transactor =>
      for {
        savedAccount <- createAccount.transact(transactor)
        savedOrgAndProject <- createOrgAndProject.transact(transactor)
        (_, savedProject) = savedOrgAndProject
        result <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NotAssignedToProject)
    }

    "reject an export for a project with no export path configured" in { transactor =>
      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(ProjectAccount(savedProject.id, savedAccount.id, nowAsInstant))
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        result <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
      } yield result shouldBe Left(ProjectError.NoExportPathConfigured)
    }

    "write one file per entity into the tree, archived tasks included" in { transactor =>
      val exportPath = newExportPath()
      val setup = for {
        projectData <- createExportableProject(exportPath)
        (savedAccount, savedProject) = projectData
        activeStatus <- createTaskStatus(savedProject.id)
        archivedStatus <- taskStatusRepo.insert(
          taskStatus.copy(projectId = savedProject.id, name = com.togger.task.DefaultTaskStatus.Archived.statusName)
        )
        label <- createTaskLabel(savedProject.id)
        milestoneId <- createMilestoneWithDate(savedProject.id)
        activeTask <- createTaskWithFullHistory(savedProject.id, savedAccount.id, activeStatus.id, Some(milestoneId))
        archivedTask <- createTaskWithFullHistory(savedProject.id, savedAccount.id, archivedStatus.id)
      } yield (savedAccount, savedProject, activeStatus, label, milestoneId, activeTask, archivedTask)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, activeStatus, label, milestoneId, activeTask, archivedTask) = setupResult
        result <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        val exported = result.toOption.get
        cp(exported.pathResults.forall(_.success) shouldBe true)
        val root = os.Path(exported.pathResults.head.exportPath)
        cp(os.exists(root / "project.json") shouldBe true)
        cp(os.exists(root / "members.json") shouldBe true)
        cp(os.exists(root / "index.json") shouldBe true)
        cp(os.exists(root / "taskstatuses" / s"${activeStatus.id}.json") shouldBe true)
        cp(os.exists(root / "tasklabels" / s"${label.id}.json") shouldBe true)
        cp(os.exists(root / "milestones" / s"$milestoneId.json") shouldBe true)
        cp(os.exists(root / "tasks" / s"$activeTask.json") shouldBe true)
        cp(os.exists(root / "tasks" / s"$archivedTask.json") shouldBe true)
        cp(os.list(root / "logentries").size shouldBe 2)
        cp(exported.entityCounts.toMap.apply(DataExportEntityType.Task) shouldBe 2)
        cp.reportAll()
      }
    }

    "rewrite nothing when the data did not change, and delete the files of removed entities" in { transactor =>
      val exportPath = newExportPath()
      val setup = for {
        projectData <- createExportableProject(exportPath)
        (savedAccount, savedProject) = projectData
        keptLabel <- createTaskLabel(savedProject.id, "Kept")
        removedLabel <- createTaskLabel(savedProject.id, "Removed")
      } yield (savedAccount, savedProject, keptLabel, removedLabel)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject, keptLabel, removedLabel) = setupResult
        service = newExportService(transactor)
        first <- service.exportProject(savedProject.id, savedAccount.id)
        root = os.Path(first.toOption.get.pathResults.head.exportPath)
        keptFile = root / "tasklabels" / s"${keptLabel.id}.json"
        removedFile = root / "tasklabels" / s"${removedLabel.id}.json"
        keptModified = os.mtime(keptFile)
        indexBefore = os.read(root / "index.json")
        _ <- service.exportProject(savedProject.id, savedAccount.id)
        _ <- taskLabelRepo.deleteById(removedLabel.id).transact(transactor)
        _ <- service.exportProject(savedProject.id, savedAccount.id)
      } yield {
        val cp = new Checkpoint
        cp(os.mtime(keptFile) shouldBe keptModified)
        cp(os.exists(removedFile) shouldBe false)
        cp(os.read(root / "index.json") should not be indexBefore)
        cp(os.read(root / "index.json") should not include removedLabel.id.toString)
        cp.reportAll()
      }
    }

  }

}
