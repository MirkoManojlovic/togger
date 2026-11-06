package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.dataimportconfig.DataImportConfig
import com.togger.dataimportconfig.DataImportConfigService
import com.togger.db.TgPages
import com.togger.project.Project
import com.togger.project.ProjectError.NoExportPathConfigured
import com.togger.project.ProjectError.NotAssignedToProject
import com.togger.project.ProjectError.ProjectError
import com.togger.project.ProjectRepo
import doobie.Transactor
import doobie.implicits._

import java.util.UUID

/** One import path of a project: either the import result or the reason it could not be imported. */
case class DataSyncImportPath(importPath: String, result: Either[ProjectError, DataImportResult])

/** The imports and then the export of one project. A project without export path has `exportResult = Right(None)`. */
case class DataSyncProjectResult(
  project: Project,
  imports: List[DataSyncImportPath],
  exportResult: Either[ProjectError, Option[DataExportResult]]
)

/** Imports every import path and then exports every export path of all the projects of an account. A failing path never
  * stops the sync; its error is part of the result.
  */
class DataSyncService(
  projectRepo: ProjectRepo,
  dataImportConfigService: DataImportConfigService,
  dataImportService: DataImportService,
  dataExportService: DataExportService,
  transactor: Transactor[IO]
) {

  def sync(accountId: UUID): IO[List[DataSyncProjectResult]] =
    for {
      projects <- TgPages.readAll[IO, Project](page =>
        projectRepo.findPaginatedByAccountId(accountId, Nil, page).transact(transactor)
      )
      results <- projects.traverse(syncProject(_, accountId))
    } yield results.flatten

  /** `None` when the account is not assigned to the project: such a project is skipped. */
  private def syncProject(project: Project, accountId: UUID): IO[Option[DataSyncProjectResult]] =
    for {
      importPaths <- TgPages
        .readAll[IO, DataImportConfig](dataImportConfigService.findPaginatedByProjectId(project.id, _))
        .map(_.map(_.importPath).distinct)
      imports <- importPaths.traverse(path =>
        dataImportService.importProject(project.id, accountId, path).map(DataSyncImportPath(path, _))
      )
      exported <- dataExportService.exportProject(project.id, accountId)
    } yield {
      val notAssigned = exported.left.toOption.contains(NotAssignedToProject)
      Option.when(!notAssigned)(DataSyncProjectResult(project, imports, toExport(exported)))
    }

  private def toExport(
    exported: Either[ProjectError, DataExportResult]
  ): Either[ProjectError, Option[DataExportResult]] =
    exported match {
      case Left(NoExportPathConfigured) => Right(None)
      case Left(error)                  => Left(error)
      case Right(result)                => Right(Some(result))
    }

}
