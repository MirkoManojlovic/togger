package com.togger.dump

import com.togger.common.TgClockImpl
import com.togger.dataimportconfig.{DataImportConfig, DataImportConfigRepoImpl, DataImportConfigService}
import com.togger.project.ProjectAccount
import doobie.implicits._

import java.util.UUID

class DataSyncPendingServiceTest extends ExportImportTestSupport {

  private val dataImportConfigRepo = new DataImportConfigRepoImpl

  private def pendingService(transactor: doobie.Transactor[cats.effect.IO]) =
    new DataSyncPendingService(
      newExportService(transactor),
      new DataImportConfigService(dataImportConfigRepo, new TgClockImpl, transactor)
    )

  private def insertImportConfig(projectId: UUID, accountId: UUID, importPath: os.Path) =
    dataImportConfigRepo.insert(
      DataImportConfig(UUID.randomUUID(), "Import config", importPath.toString, projectId, accountId, nowAsInstant, nowAsInstant)
    )

  testNameFromClassName should {

    "have something to export before the first export and nothing right after it" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (savedAccount, savedProject) = setupResult
        _ <- createTaskLabel(savedProject.id).transact(transactor)
        service = pendingService(transactor)
        before <- service.exportPending(savedProject.id, savedAccount.id)
        _ <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
        after <- service.exportPending(savedProject.id, savedAccount.id)
      } yield {
        before shouldBe Right(true)
        after shouldBe Right(false)
      }
    }

    "have nothing to export or import without any config" in { transactor =>
      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(ProjectAccount(savedProject.id, savedAccount.id, nowAsInstant))
      } yield (savedAccount, savedProject)

      for {
        setupResult <- setup.transact(transactor)
        (savedAccount, savedProject) = setupResult
        service = pendingService(transactor)
        exportResult <- service.exportPending(savedProject.id, savedAccount.id)
        importResult <- service.importPending(savedProject.id, savedAccount.id)
      } yield {
        exportResult shouldBe Right(false)
        importResult shouldBe Right(false)
      }
    }

    "have something to import only while the import path holds data the database lacks" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (savedAccount, savedProject) = setupResult
        exported <- exportToFolder(transactor, savedProject.id, savedAccount.id)
        _ <- insertImportConfig(savedProject.id, savedAccount.id, exported).transact(transactor)
        service = pendingService(transactor)
        inSync <- service.importPending(savedProject.id, savedAccount.id)
        label <- createTaskLabel(savedProject.id).transact(transactor)
        _ <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
        _ <- taskLabelRepo.deleteById(label.id).transact(transactor)
        missingFromDb <- service.importPending(savedProject.id, savedAccount.id)
      } yield {
        inSync shouldBe Right(false)
        missingFromDb shouldBe Right(true)
      }
    }

    "have something to import only when the differing entity is newer on the disk" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (savedAccount, savedProject) = setupResult
        label <- createTaskLabel(savedProject.id).transact(transactor)
        exported <- exportToFolder(transactor, savedProject.id, savedAccount.id)
        _ <- insertImportConfig(savedProject.id, savedAccount.id, exported).transact(transactor)
        service = pendingService(transactor)
        _ <- taskLabelRepo.update(label.copy(name = "Changed in db"), nowAsInstant.plusSeconds(3600)).transact(transactor)
        dbNewer <- service.importPending(savedProject.id, savedAccount.id)
        _ <- newExportService(transactor).exportProject(savedProject.id, savedAccount.id)
        _ <- taskLabelRepo.update(label.copy(name = "Changed again"), nowAsInstant.plusSeconds(1800)).transact(transactor)
        diskNewer <- service.importPending(savedProject.id, savedAccount.id)
      } yield {
        dbNewer shouldBe Right(false)
        diskNewer shouldBe Right(true)
      }
    }

    "refuse an account that is not assigned to the project" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (_, savedProject) = setupResult
        result <- pendingService(transactor).exportPending(savedProject.id, UUID.randomUUID())
      } yield result.isLeft shouldBe true
    }

  }

}
