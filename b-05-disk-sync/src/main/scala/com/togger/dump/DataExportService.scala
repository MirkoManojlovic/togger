package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigService}
import com.togger.db.TgPages
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{DumpManifestMissing, NoExportPathConfigured, NotAssignedToProject, ProjectError}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID
import scala.util.{Failure, Success, Try}

case class DataExportPathResult(
  exportPath: String,
  error: Option[String]
) {
  lazy val success: Boolean = error.isEmpty
}

object DataExportPathResult {
  def success(exportPath: String) =
    DataExportPathResult(exportPath = exportPath, error = None)

  def error(exportPath: String, error: String) =
    DataExportPathResult(exportPath = exportPath, error = Some(error))
}

case class DataExportResult(entityCounts: List[(DataExportEntityType, Int)], pathResults: List[DataExportPathResult])

class DataExportService(
  dataExportRepo: DataExportRepo,
  dataExportTreeStore: DataExportTreeStore,
  dataExportConfigService: DataExportConfigService,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  private case class ExportData(
    project: Project,
    exportedBy: AccountDumpData,
    entities: List[ExportEntity]
  )

  /** An export or status check visits every export config of the project, so all pages are read. */
  private def findAllConfigs(projectId: UUID): IO[List[DataExportConfig]] =
    TgPages.readAll[IO, DataExportConfig](dataExportConfigService.findPaginatedByProjectId(projectId, _))

  def exportProject(projectId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportResult]] =
    exportWithConfigs(projectId, accountId, findAllConfigs(projectId))

  def exportConfig(exportConfigId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportResult]] =
    dataExportConfigService.findById(exportConfigId).flatMap { config =>
      exportWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  def checkStatus(projectId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportStatusResult]] =
    checkStatusWithConfigs(projectId, accountId, findAllConfigs(projectId))

  def checkConfigStatus(exportConfigId: UUID, accountId: UUID): IO[Either[ProjectError, DataExportStatusResult]] =
    dataExportConfigService.findById(exportConfigId).flatMap { config =>
      checkStatusWithConfigs(config.projectId, accountId, IO.pure(List(config)))
    }

  private def exportWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[DataExportConfig]]
  ): IO[Either[ProjectError, DataExportResult]] = {
    logger.info(s"Exporting project ${sp(projectId)} requested by ${sp(accountId)}")
    getExportData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right(data) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoExportPathConfigured)
            else {
              val exportedAt = tgClock.currentInstant
              val pathResults = configs.map { config =>
                Try(
                  dataExportTreeStore.write(config.exportPath, data.project, exportedAt, data.exportedBy, data.entities)
                ) match {
                  case Success(written) => DataExportPathResult.success(written.path)
                  case Failure(e)       => DataExportPathResult.error(config.exportPath, e.getMessage)
                }
              }
              Right(DataExportResult(entityCounts(data.entities), pathResults))
            }
          }
      }
  }

  private def entityCounts(entities: List[ExportEntity]): List[(DataExportEntityType, Int)] =
    DataExportEntityType.all.map(entityType => entityType -> entities.count(_.entityType == entityType))

  private def checkStatusWithConfigs(
    projectId: UUID,
    accountId: UUID,
    configsIO: IO[List[DataExportConfig]]
  ): IO[Either[ProjectError, DataExportStatusResult]] = {
    logger.info(s"Checking export status for ${sp(projectId)} requested by ${sp(accountId)}")
    getExportData(projectId, accountId)
      .transact(transactor)
      .flatMap {
        case Left(error) => IO.pure(Left(error))
        case Right(data) =>
          configsIO.map { configs =>
            if (configs.isEmpty)
              Left(NoExportPathConfigured)
            else
              Right(
                DataExportStatusResult(configs.map(config => buildPathStatus(config, data)))
              )
          }
      }
  }

  /** Compares the tree at an import path with the database: what is on disk but not in the database, or differs, is
    * what an import would bring in.
    */
  def checkImportStatus(
    projectId: UUID,
    accountId: UUID,
    importPath: String
  ): IO[Either[ProjectError, DataExportPathStatus]] = {
    logger.info(s"Checking import status for ${sp(projectId)} from ${sp(importPath)} requested by ${sp(accountId)}")
    getExportData(projectId, accountId).transact(transactor).map {
      case Left(error) => Left(error)
      case Right(data) =>
        dataExportTreeStore.resolveImportFolder(importPath) match {
          case None => Left(DumpManifestMissing)
          case Some(folderPath) =>
            Right(
              DataExportPathStatus(
                importPath,
                entityStatuses(data, folderPath, dataExportTreeStore.readIndexAtPath(folderPath).map(Some(_)))
              )
            )
        }
    }
  }

  private def buildPathStatus(config: DataExportConfig, data: ExportData): DataExportPathStatus = {
    val folderPath = dataExportTreeStore.projectFolder(config.exportPath, data.project).toString
    DataExportPathStatus(
      config.exportPath,
      entityStatuses(data, folderPath, dataExportTreeStore.readIndex(config.exportPath, data.project)),
      Some(config.id)
    )
  }

  /** Compares the hashes of the database entities with the index of the tree, without reading the entity files. */
  private def entityStatuses(
    data: ExportData,
    folderPath: String,
    indexResult: Either[String, Option[ExportIndex]]
  ): List[DataExportEntityStatus] = {
    val dbEntities = data.entities.map(e => (e.entityType, e.id) -> e).toMap

    indexResult match {
      case Left(error) =>
        DataExportEntityStatus(
          DataExportEntityType.Project,
          data.project.id,
          Some(data.project.name),
          DataExportSyncStatus.Malformed,
          List(error)
        ) :: Nil
      case Right(None) =>
        data.entities.map(e => status(e.entityType, e.id, e.name, DataExportSyncStatus.MissingFromExport))
      case Right(Some(index)) =>
        DataExportEntityType.all.flatMap { entityType =>
          val indexed = index.entriesOf(entityType)
          val db = dbEntities.collect { case ((`entityType`, id), entity) => id -> entity }
          val common = db.keySet.intersect(indexed.keySet).toList.map { id =>
            val entity = db(id)
            if (indexed(id).hash != entity.hash)
              status(entityType, id, entity.name, DataExportSyncStatus.OutOfSync, List("content differs"))
            else if (!dataExportTreeStore.entityExists(folderPath, entityType, id))
              status(entityType, id, entity.name, DataExportSyncStatus.Malformed, List("file is missing"))
            else status(entityType, id, entity.name, DataExportSyncStatus.InSync)
          }
          val missingFromExport = db.keySet.diff(indexed.keySet).toList.map { id =>
            status(entityType, id, db(id).name, DataExportSyncStatus.MissingFromExport)
          }
          val missingFromDb = indexed.keySet.diff(db.keySet).toList.map { id =>
            status(entityType, id, indexed(id).name, DataExportSyncStatus.MissingFromDb)
          }
          common ++ missingFromExport ++ missingFromDb
        }
    }
  }

  private def status(
    entityType: DataExportEntityType,
    id: UUID,
    name: Option[String],
    syncStatus: DataExportSyncStatus,
    differences: List[String] = Nil
  ) = DataExportEntityStatus(entityType, id, name, syncStatus, differences)

  private def getExportData(projectId: UUID, accountId: UUID): ConnectionIO[Either[ProjectError, ExportData]] =
    dataExportRepo.isAssigned(projectId, accountId).flatMap { assigned =>
      if (!assigned)
        (NotAssignedToProject: ProjectError).asLeft[ExportData].pure[ConnectionIO]
      else
        for {
          snapshot <- dataExportRepo.readSnapshot(projectId)
          account <- dataExportRepo.findAccountById(accountId)
          referencedAccounts <- dataExportRepo.findAccounts(DataExportRepo.referencedAccountIds(snapshot))
        } yield ExportData(
          snapshot.project,
          AccountDumpData.from(account),
          DataExportEntities.build(snapshot, referencedAccounts)
        ).asRight[ProjectError]
    }

}
