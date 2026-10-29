package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{
  DataExportEntityCountResp,
  DataExportEntityStatusResp,
  DataExportEntityTypeResp,
  DataExportFieldDiffResp,
  DataExportPathResultResp,
  DataExportPathStatusResp,
  DataExportResp,
  DataExportStatusResp,
  DataExportSyncStatusResp,
  DataImportEntityResultResp,
  DataImportResp,
  DataImportStatusResp,
  DataSyncPendingResp
}
import com.togger.dump.{
  DataExportEndpoints,
  DataExportEntityStatus,
  DataExportEntityType,
  DataExportResult,
  DataExportService,
  DataExportSyncStatus,
  DataImportEntityResult,
  DataImportResult,
  DataImportService,
  DataImportStatus,
  DataSyncPendingService
}

import java.util.UUID

class DataExportController(
  dataExportEndpoints: DataExportEndpoints,
  dataExportService: DataExportService,
  dataImportService: DataImportService,
  dataSyncPendingService: DataSyncPendingService
) {

  val exportTasks = dataExportEndpoints.exportEndpoint.serverLogic { auth => projectId =>
    dataExportService
      .exportProject(projectId, auth.body.get.id)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toResp(projectId, result))
        case Left(error)   => error.toBadRequestLeft[DataExportResp]
      }
  }

  val checkStatus = dataExportEndpoints.checkStatusEndpoint.serverLogic { auth => projectId =>
    dataExportService
      .checkStatus(projectId, auth.body.get.id)
      .map {
        case Right(result) =>
          val pathStatuses =
            result.pathStatuses.map(p => DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name))
          TgJsonSuccess.justBodyRight(DataExportStatusResp(projectId, pathStatuses))
        case Left(error) => error.toBadRequestLeft[DataExportStatusResp]
      }
  }

  val exportByConfig = dataExportEndpoints.exportByConfigEndpoint.serverLogic { auth => exportConfigId =>
    dataExportService
      .exportConfig(exportConfigId, auth.body.get.id)
      .map {
        case Right(result) => TgJsonSuccess.justBodyRight(toResp(exportConfigId, result))
        case Left(error)   => error.toBadRequestLeft[DataExportResp]
      }
  }

  val checkStatusByConfig = dataExportEndpoints.checkStatusByConfigEndpoint.serverLogic { auth => exportConfigId =>
    dataExportService
      .checkConfigStatus(exportConfigId, auth.body.get.id)
      .map {
        case Right(result) =>
          val pathStatuses =
            result.pathStatuses.map(p => DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name))
          TgJsonSuccess.justBodyRight(DataExportStatusResp(exportConfigId, pathStatuses))
        case Left(error) => error.toBadRequestLeft[DataExportStatusResp]
      }
  }

  val importData = dataExportEndpoints.importEndpoint.serverLogic { auth =>
    { case (projectId, req) =>
      dataImportService
        .importProject(projectId, auth.body.get.id, req.importPath)
        .map {
          case Right(result) => TgJsonSuccess.justBodyRight(toImportResp(projectId, result))
          case Left(error)   => error.toBadRequestLeft[DataImportResp]
        }
    }
  }

  val importNewProject = dataExportEndpoints.importNewProjectEndpoint.serverLogic { auth =>
    { case (organizationId, req) =>
      dataImportService
        .importNewProject(organizationId, auth.body.get.id, req.importPath)
        .map {
          case Right((projectId, result)) => TgJsonSuccess.justBodyRight(toImportResp(projectId, result))
          case Left(error)                => error.toBadRequestLeft[DataImportResp]
        }
    }
  }

  val checkImportStatus = dataExportEndpoints.checkImportStatusEndpoint.serverLogic { auth =>
    { case (projectId, req) =>
      dataExportService
        .checkImportStatus(projectId, auth.body.get.id, req.importPath)
        .map {
          case Right(p) =>
            TgJsonSuccess.justBodyRight(
              DataExportStatusResp(projectId, List(DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name)))
            )
          case Left(error) => error.toBadRequestLeft[DataExportStatusResp]
        }
    }
  }

  val exportPending = dataExportEndpoints.exportPendingEndpoint.serverLogic { auth => projectId =>
    dataSyncPendingService
      .exportPending(projectId, auth.body.get.id)
      .map {
        case Right(pending) => TgJsonSuccess.justBodyRight(DataSyncPendingResp(projectId, pending))
        case Left(error)    => error.toBadRequestLeft[DataSyncPendingResp]
      }
  }

  val importPending = dataExportEndpoints.importPendingEndpoint.serverLogic { auth => projectId =>
    dataSyncPendingService
      .importPending(projectId, auth.body.get.id)
      .map {
        case Right(pending) => TgJsonSuccess.justBodyRight(DataSyncPendingResp(projectId, pending))
        case Left(error)    => error.toBadRequestLeft[DataSyncPendingResp]
      }
  }

  private def toImportResp(projectId: UUID, result: DataImportResult): DataImportResp = {
    val results = result.results.map(toResp)
    val counts = DataExportEntityType.all.map(t => t -> results.count(_.entityType == toResp(t)))
    DataImportResp(projectId, result.importPath, toCountsResp(counts), results)
  }

  private def toResp(id: UUID, result: DataExportResult): DataExportResp =
    DataExportResp(
      id,
      toCountsResp(result.entityCounts),
      result.pathResults.map(r => DataExportPathResultResp(r.exportPath, r.error))
    )

  private def toCountsResp(counts: List[(DataExportEntityType, Int)]): List[DataExportEntityCountResp] =
    counts.map { case (entityType, count) => DataExportEntityCountResp(toResp(entityType), count) }

  private def toResp(t: DataExportEntityType): DataExportEntityTypeResp = t match {
    case DataExportEntityType.Project      => DataExportEntityTypeResp.Project
    case DataExportEntityType.Members      => DataExportEntityTypeResp.Members
    case DataExportEntityType.TaskStatus   => DataExportEntityTypeResp.TaskStatus
    case DataExportEntityType.TaskLabel    => DataExportEntityTypeResp.TaskLabel
    case DataExportEntityType.ProjectLabel => DataExportEntityTypeResp.ProjectLabel
    case DataExportEntityType.Milestone    => DataExportEntityTypeResp.Milestone
    case DataExportEntityType.Task         => DataExportEntityTypeResp.Task
    case DataExportEntityType.LogEntry     => DataExportEntityTypeResp.LogEntry
    case DataExportEntityType.TaskTemplate => DataExportEntityTypeResp.TaskTemplate
    case DataExportEntityType.Timebox      => DataExportEntityTypeResp.Timebox
    case DataExportEntityType.TgPromptTemplate     => DataExportEntityTypeResp.TgPromptTemplate
  }

  private def toResp(r: DataImportEntityResult): DataImportEntityResultResp =
    DataImportEntityResultResp(
      toResp(r.entityType),
      r.entityId,
      r.entityName,
      toRespStatus(r.status),
      r.versionsAdded,
      r.childrenAdded,
      r.error
    )

  private def toRespStatus(s: DataImportStatus): DataImportStatusResp = s match {
    case DataImportStatus.Inserted  => DataImportStatusResp.Inserted
    case DataImportStatus.Merged    => DataImportStatusResp.Merged
    case DataImportStatus.Unchanged => DataImportStatusResp.Unchanged
    case DataImportStatus.Malformed => DataImportStatusResp.Malformed
    case DataImportStatus.Failed    => DataImportStatusResp.Failed
  }

  private def toResp(s: DataExportEntityStatus): DataExportEntityStatusResp =
    DataExportEntityStatusResp(
      toResp(s.entityType),
      s.entityId,
      s.entityName,
      toRespStatus(s.status),
      s.differences,
      s.fieldDiffs.map(d => DataExportFieldDiffResp(d.field, d.disk, d.database))
    )

  private def toRespStatus(s: DataExportSyncStatus): DataExportSyncStatusResp = s match {
    case DataExportSyncStatus.InSync            => DataExportSyncStatusResp.InSync
    case DataExportSyncStatus.OutOfSync         => DataExportSyncStatusResp.OutOfSync
    case DataExportSyncStatus.MissingFromExport => DataExportSyncStatusResp.MissingFromExport
    case DataExportSyncStatus.MissingFromDb     => DataExportSyncStatusResp.MissingFromDb
    case DataExportSyncStatus.Malformed         => DataExportSyncStatusResp.Malformed
  }

}
