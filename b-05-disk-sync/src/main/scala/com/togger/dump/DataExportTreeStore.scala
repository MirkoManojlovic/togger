package com.togger.dump

import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.util.SplunkMacro.sp
import io.circe.generic.auto._
import io.circe.parser.{decode, parse}
import io.circe.syntax._
import io.circe.Json

import java.time.Instant
import java.util.UUID
import scala.util.Try

/** One file of the export tree. The hash is taken from the canonical json, so it is the same wherever it is computed. */
case class ExportEntity(entityType: DataExportEntityType, id: UUID, name: Option[String], json: Json) {
  lazy val hash: String = ExportHasher.hashJson(json)
  lazy val changedAt: Option[Instant] = ExportHasher.latestTimestamp(json)
  def indexEntry: ExportIndexEntry = ExportIndexEntry(hash, name, changedAt)
}

case class ExportTreeWriteResult(path: String, written: Int, unchanged: Int, deleted: Int)

/** Reads and writes the live export tree of a project: `project.json`, `members.json`, `index.json` and one folder per
  * entity type holding `<id>.json` files. A file is only rewritten when its hash changed, files of entities that no
  * longer exist are deleted, and the index is written last so an interrupted export reads as out of sync.
  */
class DataExportTreeStore(diskOps: DiskOps) extends TgLogging {

  private val indexFileName = "index.json"

  def projectFolder(basePath: String, project: Project): os.Path = {
    val sanitizedName = project.name.replaceAll("[^A-Za-z0-9-_]", "-")
    diskOps.resolvePath(basePath, s"${sanitizedName}_${project.id}")
  }

  def write(
    basePath: String,
    project: Project,
    exportedAt: Instant,
    exportedBy: AccountDumpData,
    entities: List[ExportEntity]
  ): ExportTreeWriteResult = {
    val folder = projectFolder(basePath, project)
    logger.info(s"Writing export tree to ${sp(folder)}")

    val previous = readIndexFile(folder).toOption
    val previousHashes = (entity: ExportEntity) =>
      previous.flatMap(_.entriesOf(entity.entityType).get(entity.id)).map(_.hash)

    val (unchanged, changed) = entities.partition { entity =>
      previousHashes(entity).contains(entity.hash) && diskOps.exists(entityPath(folder, entity.entityType, entity.id))
    }
    changed.foreach { entity =>
      diskOps.overwriteFileToDisk(entityPath(folder, entity.entityType, entity.id), entity.json.spaces2)
    }

    val deleted = deleteStaleFiles(folder, entities)

    val entries = entities.groupBy(_.entityType.folder).map { case (typeFolder, typeEntities) =>
      typeFolder -> typeEntities.map(e => e.id.toString -> e.indexEntry).toMap
    }
    val index = ExportIndex(ExportHasher.rootHash(entries), exportedAt, exportedBy, entries)
    if (!previous.exists(_.rootHash == index.rootHash) || !diskOps.exists(folder / indexFileName))
      diskOps.overwriteFileToDisk(folder / indexFileName, index.asJson.spaces2)

    logger.info(
      s"Export tree written to ${sp(folder)}: ${sp(changed.size)} written, ${sp(unchanged.size)} unchanged, ${sp(deleted)} deleted"
    )
    ExportTreeWriteResult(folder.toString, changed.size, unchanged.size, deleted)
  }

  /** The index of the project's tree below the export path; `None` when the project was never exported there. */
  def readIndex(basePath: String, project: Project): Either[String, Option[ExportIndex]] = {
    val folder = projectFolder(basePath, project)
    if (diskOps.exists(folder / indexFileName)) readIndexFile(folder).map(Some(_)) else Right(None)
  }

  /** The project folder to import from: the path itself when it holds an index. */
  def resolveImportFolder(projectFolderPath: String): Option[String] = {
    val folder = diskOps.resolvePath(projectFolderPath)
    Option.when(diskOps.exists(folder / indexFileName))(folder.toString)
  }

  def readIndexAtPath(folderPath: String): Either[String, ExportIndex] =
    readIndexFile(diskOps.resolvePath(folderPath))

  def entityExists(folderPath: String, entityType: DataExportEntityType, id: UUID): Boolean =
    diskOps.exists(entityPath(diskOps.resolvePath(folderPath), entityType, id))

  /** The json of an entity file, only if it still matches the hash the index recorded for it. */
  def readEntityJson(
    folderPath: String,
    index: ExportIndex,
    entityType: DataExportEntityType,
    id: UUID
  ): Either[String, Json] =
    for {
      entry <- index.entriesOf(entityType).get(id).toRight(s"${entityType.folder}/$id is not in the index")
      content <- Try(diskOps.readFileFromDisk(entityPath(diskOps.resolvePath(folderPath), entityType, id))).toEither.left
        .map(_.getMessage)
      json <- parse(content).left.map(_.getMessage)
      _ <- Either.cond(ExportHasher.hashJson(json) == entry.hash, (), "file content does not match the index hash")
    } yield json

  private def readIndexFile(folder: os.Path): Either[String, ExportIndex] =
    Try(diskOps.readFileFromDisk(folder / indexFileName)).toEither.left
      .map(_.getMessage)
      .flatMap(content => decode[ExportIndex](content).left.map(_.getMessage))

  private def entityPath(folder: os.Path, entityType: DataExportEntityType, id: UUID): os.Path =
    if (entityType.singleton) folder / s"${entityType.folder}.json"
    else folder / entityType.folder / s"$id.json"

  private def deleteStaleFiles(folder: os.Path, entities: List[ExportEntity]): Int = {
    val wantedByFolder = entities.groupBy(_.entityType.folder).view.mapValues(_.map(e => s"${e.id}.json").toSet)
    DataExportEntityType.all.filterNot(_.singleton).map { entityType =>
      val wanted = wantedByFolder.getOrElse(entityType.folder, Set.empty)
      diskOps
        .listFiles(folder / entityType.folder)
        .filter(file => file.last.endsWith(".json") && !wanted.contains(file.last))
        .map { stale =>
          logger.info(s"Deleting ${sp(stale)} as its entity no longer exists")
          diskOps.deleteFile(stale)
        }
        .size
    }.sum
  }

}
