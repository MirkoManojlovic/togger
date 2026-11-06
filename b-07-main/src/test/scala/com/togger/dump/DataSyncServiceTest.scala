package com.togger.dump

import com.togger.common.TgClockImpl
import com.togger.dataimportconfig.DataImportConfig
import com.togger.dataimportconfig.DataImportConfigRepoImpl
import com.togger.dataimportconfig.DataImportConfigService
import com.togger.project.ProjectAccount
import com.togger.project.ProjectError.DumpManifestMissing
import doobie.implicits._

import java.util.UUID

class DataSyncServiceTest extends ExportImportTestSupport {

  private val dataImportConfigRepo = new DataImportConfigRepoImpl

  private def syncService(transactor: doobie.Transactor[cats.effect.IO]) = {
    val configService = new DataImportConfigService(dataImportConfigRepo, new TgClockImpl, transactor)
    new DataSyncService(
      projectRepo,
      configService,
      newImportService(transactor),
      newExportService(transactor),
      transactor
    )
  }

  private def insertImportConfig(projectId: UUID, accountId: UUID, importPath: os.Path) =
    dataImportConfigRepo.insert(
      DataImportConfig(
        UUID.randomUUID(),
        "Import config",
        importPath.toString,
        projectId,
        accountId,
        nowAsInstant,
        nowAsInstant
      )
    )

  testNameFromClassName should {

    "export the project and report that there was nothing to import without import paths" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (savedAccount, savedProject) = setupResult
        _ <- createTaskLabel(savedProject.id).transact(transactor)
        results <- syncService(transactor).sync(savedAccount.id)
      } yield {
        results.map(_.project.id) shouldBe List(savedProject.id)
        results.head.imports shouldBe Nil
        results.head.exportResult.map(_.exists(_.pathResults.forall(_.error.isEmpty))) shouldBe Right(true)
      }
    }

    "import every import path and keep going when one of them cannot be imported" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (savedAccount, savedProject) = setupResult
        exported <- exportToFolder(transactor, savedProject.id, savedAccount.id)
        missing = newExportPath()
        _ <- insertImportConfig(savedProject.id, savedAccount.id, exported).transact(transactor)
        _ <- insertImportConfig(savedProject.id, savedAccount.id, missing).transact(transactor)
        results <- syncService(transactor).sync(savedAccount.id)
      } yield {
        val imports = results.head.imports.map(i => i.importPath -> i.result).toMap
        imports(exported.toString).isRight shouldBe true
        imports(missing.toString) shouldBe Left(DumpManifestMissing)
        results.head.exportResult.isRight shouldBe true
      }
    }

    "report a project without export path as nothing to export" in { transactor =>
      val setup = for {
        savedAccount <- createAccount
        savedOrgAndProject <- createOrgAndProject
        (_, savedProject) = savedOrgAndProject
        _ <- projectAccountRepo.insert(ProjectAccount(savedProject.id, savedAccount.id, nowAsInstant))
      } yield savedAccount

      for {
        savedAccount <- setup.transact(transactor)
        results <- syncService(transactor).sync(savedAccount.id)
      } yield results.map(_.exportResult) shouldBe List(Right(None))
    }

    "skip the projects of other accounts" in { transactor =>
      for {
        setupResult <- createExportableProject(newExportPath()).transact(transactor)
        (_, _) = setupResult
        results <- syncService(transactor).sync(UUID.randomUUID())
      } yield results shouldBe Nil
    }

  }

}
