package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.{
  DataExportPathResultResp,
  DataExportPathStatusResp,
  DataExportResp,
  DataExportStatusResp,
  DataExportSyncStatusResp,
  DataExportTaskStatusResp,
  DataImportResp,
  DataImportStatusResp,
  DataImportTaskResultResp
}
import com.togger.dump.{
  DataExportEndpoints,
  DataExportService,
  DataExportSyncStatus,
  DataExportTaskStatus,
  DataImportService,
  DataImportTaskResult,
  DataImportTaskStatus
}

class DataExportController(
  dataExportEndpoints: DataExportEndpoints,
  dataExportService: DataExportService,
  dataImportService: DataImportService
) {

  val dumpTasks = dataExportEndpoints.exportEndpoint.serverLogic { auth => projectId =>
    dataExportService
      .dumpProject(projectId, auth.body.get.id)
      .map {
        case Right(result) =>
          val results = result.pathResults.map(r => DataExportPathResultResp(r.exportPath, r.error))
          TgJsonSuccess.justBodyRight(DataExportResp(projectId, result.taskCount, results))
        case Left(error) => error.toBadRequestLeft[DataExportResp]
      }
  }

  val checkStatus = dataExportEndpoints.checkStatusEndpoint.serverLogic { auth => projectId =>
    dataExportService
      .checkStatus(projectId, auth.body.get.id)
      .map {
        case Right(result) =>
          val pathStatuses = result.pathStatuses.map(p => DataExportPathStatusResp(p.exportPath, p.taskStatuses.map(toResp)))
          TgJsonSuccess.justBodyRight(DataExportStatusResp(projectId, pathStatuses))
        case Left(error) => error.toBadRequestLeft[DataExportStatusResp]
      }
  }

  val dumpByConfig = dataExportEndpoints.exportByConfigEndpoint.serverLogic { auth => dumpConfigId =>
    dataExportService
      .dumpConfig(dumpConfigId, auth.body.get.id)
      .map {
        case Right(result) =>
          val results = result.pathResults.map(r => DataExportPathResultResp(r.exportPath, r.error))
          TgJsonSuccess.justBodyRight(DataExportResp(dumpConfigId, result.taskCount, results))
        case Left(error) => error.toBadRequestLeft[DataExportResp]
      }
  }

  val checkStatusByConfig = dataExportEndpoints.checkStatusByConfigEndpoint.serverLogic { auth => dumpConfigId =>
    dataExportService
      .checkConfigStatus(dumpConfigId, auth.body.get.id)
      .map {
        case Right(result) =>
          val pathStatuses = result.pathStatuses.map(p => DataExportPathStatusResp(p.exportPath, p.taskStatuses.map(toResp)))
          TgJsonSuccess.justBodyRight(DataExportStatusResp(dumpConfigId, pathStatuses))
        case Left(error) => error.toBadRequestLeft[DataExportStatusResp]
      }
  }

  val importData = dataExportEndpoints.importEndpoint.serverLogic { auth =>
    { case (projectId, req) =>
      dataImportService
        .importProject(projectId, auth.body.get.id, req.importPath)
        .map {
          case Right(result) =>
            val results = result.taskResults.map(toResp)
            TgJsonSuccess.justBodyRight(DataImportResp(projectId, result.dumpPath, result.taskCount, results))
          case Left(error) => error.toBadRequestLeft[DataImportResp]
        }
    }
  }

  private def toResp(r: DataImportTaskResult): DataImportTaskResultResp =
    DataImportTaskResultResp(
      r.taskId,
      r.taskName,
      toRespStatus(r.status),
      r.versionsAdded,
      r.linksAdded,
      r.logEntriesAdded,
      r.error
    )

  private def toRespStatus(s: DataImportTaskStatus): DataImportStatusResp = s match {
    case DataImportTaskStatus.Inserted  => DataImportStatusResp.Inserted
    case DataImportTaskStatus.Merged    => DataImportStatusResp.Merged
    case DataImportTaskStatus.Unchanged => DataImportStatusResp.Unchanged
    case DataImportTaskStatus.Malformed => DataImportStatusResp.Malformed
    case DataImportTaskStatus.Failed    => DataImportStatusResp.Failed
  }

  private def toResp(s: DataExportTaskStatus): DataExportTaskStatusResp =
    DataExportTaskStatusResp(s.taskId, s.taskName, toRespStatus(s.status), s.differences)

  private def toRespStatus(s: DataExportSyncStatus): DataExportSyncStatusResp = s match {
    case DataExportSyncStatus.InSync          => DataExportSyncStatusResp.InSync
    case DataExportSyncStatus.OutOfSync       => DataExportSyncStatusResp.OutOfSync
    case DataExportSyncStatus.MissingFromExport => DataExportSyncStatusResp.MissingFromExport
    case DataExportSyncStatus.MissingFromDb   => DataExportSyncStatusResp.MissingFromDb
    case DataExportSyncStatus.Malformed       => DataExportSyncStatusResp.Malformed
  }

}
