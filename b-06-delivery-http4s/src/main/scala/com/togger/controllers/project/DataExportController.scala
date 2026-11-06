package com.togger.controllers.project

import com.togger.common.TgError._
import com.togger.common.TgJsonSuccess
import com.togger.dto.DataExportEntityCountResp
import com.togger.dto.DataExportEntityStatusResp
import com.togger.dto.DataExportEntityTypeResp
import com.togger.dto.DataExportFieldDiffResp
import com.togger.dto.DataExportPathResultResp
import com.togger.dto.DataExportPathStatusResp
import com.togger.dto.DataExportResp
import com.togger.dto.DataExportStatusResp
import com.togger.dto.DataExportSyncStatusResp
import com.togger.dto.DataImportEntityResultResp
import com.togger.dto.DataImportPathResp
import com.togger.dto.DataImportResp
import com.togger.dto.DataImportStatusResp
import com.togger.dto.DataSyncPendingResp
import com.togger.dto.DataSyncProjectResp
import com.togger.dto.DataSyncResp
import com.togger.dump.DataExportEndpoints
import com.togger.dump.DataExportEntityStatus
import com.togger.dump.DataExportEntityType
import com.togger.dump.DataExportResult
import com.togger.dump.DataExportService
import com.togger.dump.DataExportSyncStatus
import com.togger.dump.DataImportEntityResult
import com.togger.dump.DataImportResult
import com.togger.dump.DataImportService
import com.togger.dump.DataImportStatus
import com.togger.dump.DataSyncPendingService
import com.togger.dump.DataSyncProjectResult
import com.togger.dump.DataSyncService

import java.util.UUID

class DataExportController(
  dataExportEndpoints: DataExportEndpoints,
  dataExportService: DataExportService,
  dataImportService: DataImportService,
  dataSyncPendingService: DataSyncPendingService,
  dataSyncService: DataSyncService
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
            result.pathStatuses
              .map(p => DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name))
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
            result.pathStatuses
              .map(p => DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name))
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
              DataExportStatusResp(
                projectId,
                List(DataExportPathStatusResp(p.exportPath, p.entityStatuses.map(toResp), p.exportConfigId, p.name))
              )
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

  val sync = dataExportEndpoints.syncEndpoint.serverLogic { auth => _ =>
    dataSyncService
      .sync(auth.body.get.id)
      .map(results => TgJsonSuccess.justBodyRight(DataSyncResp(results.map(toResp))))
  }

  private def toResp(result: DataSyncProjectResult): DataSyncProjectResp = {
    val projectId = result.project.id
    DataSyncProjectResp(
      projectId,
      result.project.name,
      result.imports.map(i =>
        DataImportPathResp(
          i.importPath,
          i.result.toOption.map(toImportResp(projectId, _)),
          i.result.left.toOption.map(_.message)
        )
      ),
      result.exportResult.toOption.flatten.map(toResp(projectId, _)),
      result.exportResult.left.toOption.map(_.message)
    )
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
    case DataExportEntityType.Project               => DataExportEntityTypeResp.Project
    case DataExportEntityType.Members               => DataExportEntityTypeResp.Members
    case DataExportEntityType.TaskStatus            => DataExportEntityTypeResp.TaskStatus
    case DataExportEntityType.TaskLabel             => DataExportEntityTypeResp.TaskLabel
    case DataExportEntityType.ProjectLabel          => DataExportEntityTypeResp.ProjectLabel
    case DataExportEntityType.Milestone             => DataExportEntityTypeResp.Milestone
    case DataExportEntityType.Task                  => DataExportEntityTypeResp.Task
    case DataExportEntityType.LogEntry              => DataExportEntityTypeResp.LogEntry
    case DataExportEntityType.TaskTemplate          => DataExportEntityTypeResp.TaskTemplate
    case DataExportEntityType.MilestoneStepTemplate => DataExportEntityTypeResp.MilestoneStepTemplate
    case DataExportEntityType.MilestoneTemplate     => DataExportEntityTypeResp.MilestoneTemplate
    case DataExportEntityType.Timebox               => DataExportEntityTypeResp.Timebox
    case DataExportEntityType.TgPromptTemplate      => DataExportEntityTypeResp.TgPromptTemplate
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
