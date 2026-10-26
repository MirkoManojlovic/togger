package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.dataimportconfig.{DataImportConfig, DataImportConfigService}
import com.togger.db.TgPages
import com.togger.project.ProjectError.{NoExportPathConfigured, NotAssignedToProject, ProjectError}

import java.util.UUID

/** Answers only whether an export or import has something to do, for the periodic check of the frontend. */
class DataSyncPendingService(
  dataExportService: DataExportService,
  dataImportConfigService: DataImportConfigService
) {

  private val exportNeeded: Set[DataExportSyncStatus] =
    Set(DataExportSyncStatus.OutOfSync, DataExportSyncStatus.MissingFromExport, DataExportSyncStatus.Malformed)

  private val importNeeded: Set[DataExportSyncStatus] =
    Set(DataExportSyncStatus.OutOfSync, DataExportSyncStatus.MissingFromDb)

  private def anyNeeded(pathStatus: DataExportPathStatus, needed: Set[DataExportSyncStatus]): Boolean =
    pathStatus.entityStatuses.exists(s => needed.contains(s.status))

  def exportPending(projectId: UUID, accountId: UUID): IO[Either[ProjectError, Boolean]] =
    dataExportService.checkStatus(projectId, accountId).map {
      case Right(result)                => Right(result.pathStatuses.exists(anyNeeded(_, exportNeeded)))
      case Left(NoExportPathConfigured) => Right(false)
      case Left(error)                  => Left(error)
    }

  def importPending(projectId: UUID, accountId: UUID): IO[Either[ProjectError, Boolean]] =
    for {
      configs <- TgPages.readAll[IO, DataImportConfig](dataImportConfigService.findPaginatedByProjectId(projectId, _))
      results <- configs.map(_.importPath).distinct.traverse(dataExportService.checkImportStatus(projectId, accountId, _))
    } yield results
      .collectFirst { case Left(NotAssignedToProject) => NotAssignedToProject }
      .toLeft(results.exists {
        case Right(pathStatus) => anyNeeded(pathStatus, importNeeded)
        case Left(_)           => false // e.g. nothing exported at that path yet
      })

}
