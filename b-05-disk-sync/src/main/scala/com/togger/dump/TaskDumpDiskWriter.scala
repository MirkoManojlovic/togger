package com.togger.dump

import com.togger.dump.DayOfWeekCodec._
import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.util.SplunkMacro.sp
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.util.Try

class TaskDumpDiskWriter(diskOps: DiskOps, dumpLimit: Int) extends TgLogging {

  require(dumpLimit > 0, s"dumpLimit must be positive, got $dumpLimit")

  private val dumpFolderFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

  def write(basePath: String, manifest: ProjectDumpManifest, tasks: List[TaskDumpData]): String = {
    val projectFolder = diskOps.resolvePath(basePath, projectFolderName(manifest.project))
    val rootFolder = projectFolder / dumpFolderFormatter.format(manifest.dumpedAt)
    logger.info(s"Writing task dump to ${sp(rootFolder)}")

    diskOps.overwriteFileToDisk(rootFolder / "project.json", manifest.asJson.spaces2)
    tasks.foreach { taskDump =>
      diskOps.overwriteFileToDisk(rootFolder / s"${taskDump.task.id}.json", taskDump.asJson.spaces2)
    }

    logger.info(s"Task dump written to ${sp(rootFolder)} with ${sp(tasks.size)} tasks")
    pruneOldDumps(projectFolder)
    rootFolder.toString
  }

  def readDumpedTasks(basePath: String, project: Project): Map[UUID, Either[String, TaskDumpData]] = {
    val projectFolder = diskOps.resolvePath(basePath, projectFolderName(project))
    latestDumpFolder(projectFolder) match {
      case Some(rootFolder) =>
        diskOps
          .listFiles(rootFolder)
          .flatMap { file =>
            Try(UUID.fromString(file.baseName)).toOption.map(taskId => taskId -> parseTaskDumpFile(file))
          }
          .toMap
      case None => Map.empty
    }
  }

  def resolveLatestDumpFolder(projectRootPath: String): Option[String] =
    latestDumpFolder(diskOps.resolvePath(projectRootPath)).map(_.toString)

  def readManifest(folderPath: String): Either[String, ProjectDumpManifest] =
    Try(diskOps.readFileFromDisk(diskOps.resolvePath(folderPath, "project.json"))).toEither.left
      .map(_.getMessage)
      .flatMap(content => decode[ProjectDumpManifest](content).left.map(_.getMessage))

  def readDumpedTasksAtPath(folderPath: String): Map[UUID, Either[String, TaskDumpData]] =
    diskOps
      .listFiles(diskOps.resolvePath(folderPath))
      .flatMap { file =>
        Try(UUID.fromString(file.baseName)).toOption.map(taskId => taskId -> parseTaskDumpFile(file))
      }
      .toMap

  private def parseTaskDumpFile(file: os.Path): Either[String, TaskDumpData] =
    Try(diskOps.readFileFromDisk(file)).toEither.left
      .map(_.getMessage)
      .flatMap(content => decode[TaskDumpData](content).left.map(_.getMessage))

  private def projectFolderName(project: Project): String = {
    val sanitizedName = project.name.replaceAll("[^A-Za-z0-9-_]", "-")
    s"${sanitizedName}_${project.id}"
  }

  private def dumpFoldersNewestFirst(projectFolder: os.Path): Seq[os.Path] =
    diskOps
      .listDirs(projectFolder)
      .filter(_.last.matches("\\d{14}"))
      .sortBy(_.last)(Ordering[String].reverse)

  private def latestDumpFolder(projectFolder: os.Path): Option[os.Path] =
    dumpFoldersNewestFirst(projectFolder).headOption

  private def pruneOldDumps(projectFolder: os.Path): Unit =
    dumpFoldersNewestFirst(projectFolder).drop(dumpLimit).foreach { staleFolder =>
      logger.info(s"Deleting dump ${sp(staleFolder)} as it exceeds dump limit of ${sp(dumpLimit)}")
      diskOps.deleteDir(staleFolder)
    }

}
