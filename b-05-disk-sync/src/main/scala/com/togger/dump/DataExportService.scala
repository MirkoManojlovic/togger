package com.togger.dump

import cats.effect.IO
import cats.implicits._
import com.togger.common.TgClock
import com.togger.dataexportconfig.DataExportConfig
import com.togger.dataexportconfig.DataExportConfigService
import com.togger.db.TgPages
import com.togger.logging.TgLogging
import com.togger.deletedentity.DeletedEntity
import com.togger.project.Project
import com.togger.project.ProjectError.DumpManifestMissing
import com.togger.project.ProjectError.ImportFolderReadTimeout
import com.togger.project.ProjectError.NoExportPathConfigured
import com.togger.project.ProjectError.NotAssignedToProject
import com.togger.project.ProjectError.ProjectError
import com.togger.util.SplunkMacro.sp
import doobie.ConnectionIO
import doobie.Transactor
import doobie.implicits._
import io.circe.Json

import java.util.UUID
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

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
    entities: List[ExportEntity],
    deletedEntities: List[DeletedEntity]
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
                  dataExportTreeStore.write(
                    config.exportPath,
                    data.project,
                    exportedAt,
                    data.exportedBy,
                    data.entities,
                    data.deletedEntities
                  )
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

  /** Compares the tree at an import path with the database: what is on disk but not in the database, or differs and is
    * newer on disk, is what an import would bring in. An entity that differs but is newer in the database is reported
    * as in sync, as there is nothing to import.
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
          IO(
            logger.error(s"Reading import folder timed out after [timeout=$importFolderReadTimeout] ${sp(importPath)}")
          )
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

  /** A differing entity is only importable when the disk holds the newer change; without timestamps to compare, it is.
    */
  private def withoutOlderDisk(data: ExportData, indexResult: Either[String, Option[ExportIndex]])(
    entityStatus: DataExportEntityStatus
  ): DataExportEntityStatus = {
    val dbChangedAt = data.entities
      .find(e => e.entityType == entityStatus.entityType && e.id == entityStatus.entityId)
      .flatMap(_.changedAt)
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
    lazy val accountKeys = if (matchByContent) importAccountKeys(data, folderPath, indexResult) else None
    lazy val labelKeys = taskLabelKeys(data, folderPath, indexResult)
    lazy val projectLabelNames = if (matchByContent) importProjectLabelNames(data, indexResult) else None

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
            importedProjectStatus(
              folderPath,
              index,
              db.values.head,
              indexed.keys.head,
              accountKeys,
              projectLabelNames
            ) :: Nil
          else if (matchByContent && entityType == DataExportEntityType.Members && db.size == 1 && indexed.size == 1)
            importedMembersStatus(folderPath, index, db.values.head, indexed.keys.head, accountKeys) :: Nil
          else {
            val common = db.keySet.intersect(indexed.keySet).toList.map { id =>
              val entity = db(id)
              if (indexed(id).hash != entity.hash)
                outOfSyncStatus(
                  folderPath,
                  index,
                  entity,
                  if (matchByContent) importIgnoredFields else Set.empty,
                  entity.id,
                  accountKeys
                )
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

  /** The content keys of the task labels and milestone step templates of the database and of the disk, as a template
    * names its label or steps by id.
    */
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
    val dbStepTemplates = data.entities.collect {
      case e if e.entityType == DataExportEntityType.MilestoneStepTemplate => e.id -> e.json
    }
    val diskStepTemplates = indexResult.toOption.flatten.toList.flatMap { index =>
      index.entriesOf(DataExportEntityType.MilestoneStepTemplate).keys.toList.flatMap { id =>
        dataExportTreeStore
          .readEntityJson(folderPath, index, DataExportEntityType.MilestoneStepTemplate, id)
          .toOption
          .map(id -> _)
      }
    }
    (
      ExportContentKey.labelKeys(dbLabels.toMap) ++ ExportContentKey.stepTemplateKeys(dbStepTemplates.toMap),
      ExportContentKey.labelKeys(diskLabels.toMap) ++ ExportContentKey.stepTemplateKeys(diskStepTemplates.toMap)
    )
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
      dbEntities
        .flatMap(e => ExportContentKey.of(entityType, e.json, dbLabelKeys).toOption.map(_ -> e))
        .groupMap(_._1)(_._2)
    val diskKeys = indexed.keys.toList.sorted.map { id =>
      id -> dataExportTreeStore
        .readEntityJson(folderPath, index, entityType, id)
        .flatMap(ExportContentKey.of(entityType, _, diskLabelKeys))
    }

    val (unmatchedDiskIds, results, remaining) =
      diskKeys.foldLeft((List.empty[UUID], List.empty[DataExportEntityStatus], dbByKey)) {
        case ((missing, matched, available), (id, Left(error))) =>
          (
            missing,
            status(entityType, id, indexed(id).name, DataExportSyncStatus.Malformed, List(error)) :: matched,
            available
          )
        case ((missing, matched, available), (id, Right(key))) =>
          available.getOrElse(key, Nil) match {
            case dbEntity :: rest =>
              (
                missing,
                status(entityType, dbEntity.id, dbEntity.name, DataExportSyncStatus.InSync) :: matched,
                available.updated(key, rest)
              )
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

  /** The import check does not report a project that was renamed, nor the id, organization and timestamps it had when
    * exported: an import into a new project gives it a new id and timestamps, and the organization may be another one.
    */
  private val importIgnoredFields =
    Set("project.name", "project.id", "project.organizationId", "project.createdAt", "project.updatedAt")

  /** The project of an import folder is the database project whatever its id, as an import into a new project gives it
    * a new one.
    */
  private def importedProjectStatus(
    folderPath: String,
    index: ExportIndex,
    entity: ExportEntity,
    diskId: UUID,
    accountKeys: Option[(ExportAccountKeys, ExportAccountKeys)],
    projectLabelNames: Option[(Map[UUID, String], Map[UUID, String])]
  ) = outOfSyncStatus(folderPath, index, entity, importIgnoredFields, diskId, accountKeys, projectLabelNames)

  /** The names of the project labels of the disk and of the database by id: the import matches project labels by name,
    * so the project lists the same labels when their names are the same, whatever their ids.
    */
  private def importProjectLabelNames(
    data: ExportData,
    indexResult: Either[String, Option[ExportIndex]]
  ): Option[(Map[UUID, String], Map[UUID, String])] =
    indexResult.toOption.flatten.map { index =>
      val disk = index.entriesOf(DataExportEntityType.ProjectLabel).map { case (id, entry) =>
        id -> entry.name.getOrElse(id.toString)
      }
      val database = data.entities.collect {
        case e if e.entityType == DataExportEntityType.ProjectLabel => e.id -> e.name.getOrElse(e.id.toString)
      }.toMap
      (disk, database)
    }

  /** The `projectLabelIds` of a project file as the sorted names of its labels, so that equal labels compare equal. */
  private def withProjectLabelNames(json: Json, names: Map[UUID, String]): Json =
    json.hcursor.get[List[UUID]]("projectLabelIds") match {
      case Right(ids) =>
        json.mapObject(
          _.add(
            "projectLabelIds",
            Json.fromValues(ids.map(id => names.getOrElse(id, id.toString)).sorted.map(Json.fromString))
          )
        )
      case Left(_) => json
    }

  /** The members file of an import folder is the one of the database project whatever its id, as an import into another
    * project gives it a new one. An import only adds members, so members that the database has and the disk lacks are
    * not reported: the entity is out of sync only when the disk lists an account that is not a member yet. Accounts are
    * compared by their content, as the import maps them by email.
    */
  private def importedMembersStatus(
    folderPath: String,
    index: ExportIndex,
    entity: ExportEntity,
    diskId: UUID,
    accountKeys: Option[(ExportAccountKeys, ExportAccountKeys)]
  ): DataExportEntityStatus =
    dataExportTreeStore.readEntityJson(folderPath, index, entity.entityType, diskId) match {
      case Right(diskJson) =>
        val (diskCompared, databaseCompared) = accountKeys.fold((diskJson, entity.json)) { case (disk, database) =>
          (disk.replaceIds(diskJson), database.replaceIds(entity.json))
        }
        def accounts(json: Json): Set[String] = json.hcursor.get[Set[String]]("accountIds").getOrElse(Set.empty)
        if (accounts(diskCompared).subsetOf(accounts(databaseCompared)))
          status(entity.entityType, entity.id, entity.name, DataExportSyncStatus.InSync)
        else
          status(
            entity.entityType,
            entity.id,
            entity.name,
            DataExportSyncStatus.OutOfSync,
            List("content differs"),
            ExportJsonDiff.diff(diskCompared, databaseCompared)
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

  /** The accounts of the disk and of the database by their content, from the project file of each side. */
  private def importAccountKeys(
    data: ExportData,
    folderPath: String,
    indexResult: Either[String, Option[ExportIndex]]
  ): Option[(ExportAccountKeys, ExportAccountKeys)] =
    for {
      index <- indexResult.toOption.flatten
      diskProjectId <- index.entriesOf(DataExportEntityType.Project).keys.headOption
      diskJson <- dataExportTreeStore
        .readEntityJson(folderPath, index, DataExportEntityType.Project, diskProjectId)
        .toOption
      dbProject <- data.entities.find(_.entityType == DataExportEntityType.Project)
    } yield (ExportAccountKeys.ofProject(diskJson), ExportAccountKeys.ofProject(dbProject.json))

  /** The field diffs need the entity file, which can be unreadable; the status is then out of sync without them. An
    * entity whose only differences are in `ignoredFields`, or in the ids of equal accounts, is in sync.
    */
  private def outOfSyncStatus(
    folderPath: String,
    index: ExportIndex,
    entity: ExportEntity,
    ignoredFields: Set[String] = Set.empty,
    diskId: UUID,
    accountKeys: Option[(ExportAccountKeys, ExportAccountKeys)] = None,
    projectLabelNames: Option[(Map[UUID, String], Map[UUID, String])] = None
  ): DataExportEntityStatus =
    dataExportTreeStore.readEntityJson(folderPath, index, entity.entityType, diskId) match {
      case Right(diskJson) =>
        val (diskIdsReplaced, databaseIdsReplaced) = accountKeys.fold((diskJson, entity.json)) {
          case (disk, database) => (disk.replaceIds(diskJson), database.replaceIds(entity.json))
        }
        val (diskCompared, databaseCompared) = projectLabelNames.fold((diskIdsReplaced, databaseIdsReplaced)) {
          case (diskNames, databaseNames) =>
            (
              withProjectLabelNames(diskIdsReplaced, diskNames),
              withProjectLabelNames(databaseIdsReplaced, databaseNames)
            )
        }
        val fieldDiffs =
          ExportJsonDiff.diff(diskCompared, databaseCompared).filterNot(d => ignoredFields.contains(d.field))
        if (fieldDiffs.isEmpty && (ignoredFields.nonEmpty || accountKeys.isDefined))
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
          DataExportEntities.build(snapshot, referencedAccounts),
          snapshot.deletedEntities
        ).asRight[ProjectError]
    }

}
