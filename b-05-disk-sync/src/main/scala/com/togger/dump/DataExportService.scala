package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.dataexportconfig.{DataExportConfig, DataExportConfigService}
import com.togger.db.TgPages
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.project.ProjectError.{DumpManifestMissing, ImportFolderReadTimeout, NoExportPathConfigured, NotAssignedToProject, ProjectError}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID
import scala.concurrent.duration._
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

  /** Compares the tree at an import path with the database: what is on disk but not in the database, or differs and
    * is newer on disk, is what an import would bring in. An entity that differs but is newer in the database is
    * reported as in sync, as there is nothing to import.
    */
  def checkImportStatus(
    projectId: UUID,
    accountId: UUID,
    importPath: String
  ): IO[Either[ProjectError, DataExportPathStatus]] = {
    logger.info(s"Checking import status for ${sp(projectId)} from ${sp(importPath)} requested by ${sp(accountId)}")
    getExportData(projectId, accountId).transact(transactor).flatMap {
      case Left(error) => IO.pure(Left(error))
      case Right(data) =>
        logger.info(s"Loaded database entities for import check [entities.size=${data.entities.size}]")
        readImportFolder(data, importPath).timeoutTo(
          importFolderReadTimeout,
          IO(logger.error(s"Reading import folder timed out after [timeout=$importFolderReadTimeout] ${sp(importPath)}"))
            .as(Left(ImportFolderReadTimeout))
        )
    }
  }

  private val importFolderReadTimeout = 60.seconds

  /** Reads the import folder on the blocking pool and can be interrupted, as reads of a cloud folder can hang. */
  private def readImportFolder(
    data: ExportData,
    importPath: String
  ): IO[Either[ProjectError, DataExportPathStatus]] =
    IO.interruptible {
      val startedAt = System.nanoTime()
      logger.info(s"Reading import folder ${sp(importPath)}")
      val result = dataExportTreeStore.resolveImportFolder(importPath) match {
        case None =>
          logger.warn(s"No index file found in import folder ${sp(importPath)}")
          Left(DumpManifestMissing)
        case Some(folderPath) =>
          val indexResult = dataExportTreeStore.readIndexAtPath(folderPath).map(Some(_))
          logger.info(s"Read import index of ${sp(folderPath)} [ok=${indexResult.isRight}]")
          val statuses = entityStatuses(data, folderPath, indexResult, matchByContent = true)
            .map(withoutOlderDisk(data, indexResult))
          Right(DataExportPathStatus(importPath, statuses))
      }
      val tookMs = (System.nanoTime() - startedAt) / 1000000
      logger.info(s"Read import folder ${sp(importPath)} [tookMs=$tookMs]")
      result
    }

  /** A differing entity is only importable when the disk holds the newer change; without timestamps to compare, it is. */
  private def withoutOlderDisk(data: ExportData, indexResult: Either[String, Option[ExportIndex]])(
    entityStatus: DataExportEntityStatus
  ): DataExportEntityStatus = {
    val dbChangedAt = data.entities.find(e => e.entityType == entityStatus.entityType && e.id == entityStatus.entityId).flatMap(_.changedAt)
    val diskChangedAt = indexResult.toOption.flatten
      .flatMap(_.entriesOf(entityStatus.entityType).get(entityStatus.entityId))
      .flatMap(_.changedAt)
    val diskIsOlder = (diskChangedAt, dbChangedAt) match {
      case (Some(disk), Some(db)) => !disk.isAfter(db)
      case _                      => false
    }
    if (entityStatus.status == DataExportSyncStatus.OutOfSync && diskIsOlder)
      entityStatus.copy(
        status = DataExportSyncStatus.InSync,
        differences = List("database is newer than the disk"),
        fieldDiffs = Nil
      )
    else entityStatus
  }

  private def buildPathStatus(config: DataExportConfig, data: ExportData): DataExportPathStatus = {
    val folderPath = dataExportTreeStore.projectFolder(config.exportPath, data.project).toString
    DataExportPathStatus(
      config.exportPath,
      entityStatuses(data, folderPath, dataExportTreeStore.readIndex(config.exportPath, data.project)),
      Some(config.id),
      Some(config.name)
    )
  }

  /** Compares the hashes of the database entities with the index of the tree, without reading the entity files. With
    * `matchByContent`, task statuses and labels are instead matched by name, description and emoji, as an import gives
    * them new ids and timestamps.
    */
  private def entityStatuses(
    data: ExportData,
    folderPath: String,
    indexResult: Either[String, Option[ExportIndex]],
    matchByContent: Boolean = false
  ): List[DataExportEntityStatus] = {
    val dbEntities = data.entities.map(e => (e.entityType, e.id) -> e).toMap
    lazy val labelKeys = taskLabelKeys(data, folderPath, indexResult)

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
          if (matchByContent && ExportContentKey.applies(entityType))
            contentStatuses(entityType, folderPath, index, db.values.toList.sortBy(_.id), indexed, labelKeys)
          else if (matchByContent && entityType == DataExportEntityType.Project && db.size == 1 && indexed.size == 1)
            importedProjectStatus(folderPath, index, db.values.head, indexed.keys.head) :: Nil
          else {
          val common = db.keySet.intersect(indexed.keySet).toList.map { id =>
            val entity = db(id)
            if (indexed(id).hash != entity.hash)
              outOfSyncStatus(folderPath, index, entity, if (matchByContent) importIgnoredFields else Set.empty, entity.id)
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
  }

  /** The content keys of the task labels of the database and of the disk, as a template names its label by id. */
  private def taskLabelKeys(
    data: ExportData,
    folderPath: String,
    indexResult: Either[String, Option[ExportIndex]]
  ): (Map[UUID, ExportContentKey.Key], Map[UUID, ExportContentKey.Key]) = {
    val dbLabels = data.entities.collect { case e if e.entityType == DataExportEntityType.TaskLabel => e.id -> e.json }
    val diskLabels = indexResult.toOption.flatten.toList.flatMap { index =>
      index.entriesOf(DataExportEntityType.TaskLabel).keys.toList.flatMap { id =>
        dataExportTreeStore.readEntityJson(folderPath, index, DataExportEntityType.TaskLabel, id).toOption.map(id -> _)
      }
    }
    (ExportContentKey.labelKeys(dbLabels.toMap), ExportContentKey.labelKeys(diskLabels.toMap))
  }

  /** Each disk entity is paired with one database entity of the same content key, whatever their ids. */
  private def contentStatuses(
    entityType: DataExportEntityType,
    folderPath: String,
    index: ExportIndex,
    dbEntities: List[ExportEntity],
    indexed: Map[UUID, ExportIndexEntry],
    labelKeys: (Map[UUID, ExportContentKey.Key], Map[UUID, ExportContentKey.Key])
  ): List[DataExportEntityStatus] = {
    val (dbLabelKeys, diskLabelKeys) = labelKeys
    val dbByKey =
      dbEntities.flatMap(e => ExportContentKey.of(entityType, e.json, dbLabelKeys).toOption.map(_ -> e)).groupMap(_._1)(_._2)
    val diskKeys = indexed.keys.toList.sorted.map { id =>
      id -> dataExportTreeStore
        .readEntityJson(folderPath, index, entityType, id)
        .flatMap(ExportContentKey.of(entityType, _, diskLabelKeys))
    }

    val (unmatchedDiskIds, results, remaining) =
      diskKeys.foldLeft((List.empty[UUID], List.empty[DataExportEntityStatus], dbByKey)) {
        case ((missing, matched, available), (id, Left(error))) =>
          (missing, status(entityType, id, indexed(id).name, DataExportSyncStatus.Malformed, List(error)) :: matched, available)
        case ((missing, matched, available), (id, Right(key))) =>
          available.getOrElse(key, Nil) match {
            case dbEntity :: rest =>
              (missing, status(entityType, dbEntity.id, dbEntity.name, DataExportSyncStatus.InSync) :: matched, available.updated(key, rest))
            case Nil =>
              (id :: missing, matched, available)
          }
      }
    // what matches by no content but by id is a changed entity, which the timestamps then tell apart from a stale disk
    val remainingDb = remaining.values.flatten.toList.sortBy(_.id)
    val remainingDbById = remainingDb.map(e => e.id -> e).toMap
    val unmatchedDisk = unmatchedDiskIds.reverse.map { id =>
      remainingDbById.get(id) match {
        case Some(entity) => outOfSyncStatus(folderPath, index, entity, diskId = entity.id)
        case None         => status(entityType, id, indexed(id).name, DataExportSyncStatus.MissingFromDb)
      }
    }
    val missingFromExport = remainingDb.filterNot(e => unmatchedDiskIds.contains(e.id)).map { e =>
      status(entityType, e.id, e.name, DataExportSyncStatus.MissingFromExport)
    }
    results.reverse ++ unmatchedDisk ++ missingFromExport
  }

  /** The import check does not report a project that was renamed, nor the id it had when exported. */
  private val importIgnoredFields = Set("project.name", "project.id")

  /** The project of an import folder is the database project whatever its id, as an import into a new project gives it a new one. */
  private def importedProjectStatus(folderPath: String, index: ExportIndex, entity: ExportEntity, diskId: UUID) =
    outOfSyncStatus(folderPath, index, entity, importIgnoredFields, diskId)

  /** The field diffs need the entity file, which can be unreadable; the status is then out of sync without them. An
    * entity whose only differences are in `ignoredFields` is in sync.
    */
  private def outOfSyncStatus(
    folderPath: String,
    index: ExportIndex,
    entity: ExportEntity,
    ignoredFields: Set[String] = Set.empty,
    diskId: UUID
  ): DataExportEntityStatus =
    dataExportTreeStore.readEntityJson(folderPath, index, entity.entityType, diskId) match {
      case Right(diskJson) =>
        val fieldDiffs = ExportJsonDiff.diff(diskJson, entity.json).filterNot(d => ignoredFields.contains(d.field))
        if (fieldDiffs.isEmpty && ignoredFields.nonEmpty)
          status(entity.entityType, entity.id, entity.name, DataExportSyncStatus.InSync)
        else
          status(
            entity.entityType,
            entity.id,
            entity.name,
            DataExportSyncStatus.OutOfSync,
            List("content differs"),
            fieldDiffs
          )
      case Left(error) =>
        status(
          entity.entityType,
          entity.id,
          entity.name,
          DataExportSyncStatus.OutOfSync,
          List("content differs", s"cannot compare: $error")
        )
    }

  private def status(
    entityType: DataExportEntityType,
    id: UUID,
    name: Option[String],
    syncStatus: DataExportSyncStatus,
    differences: List[String] = Nil,
    fieldDiffs: List[ExportFieldDiff] = Nil
  ) = DataExportEntityStatus(entityType, id, name, syncStatus, differences, fieldDiffs)

  private def getExportData(projectId: UUID, accountId: UUID): ConnectionIO[Either[ProjectError, ExportData]] =
    dataExportRepo.isAssigned(projectId, accountId).flatMap { assigned =>
      if (!assigned)
        (NotAssignedToProject: ProjectError).asLeft[ExportData].pure[ConnectionIO]
      else
        for {
          snapshot <- dataExportRepo.readSnapshot(projectId, accountId)
          account <- dataExportRepo.findAccountById(accountId)
          referencedAccounts <- dataExportRepo.findAccounts(DataExportRepo.referencedAccountIds(snapshot))
        } yield ExportData(
          snapshot.project,
          AccountDumpData.from(account),
          DataExportEntities.build(snapshot, referencedAccounts)
        ).asRight[ProjectError]
    }

}
