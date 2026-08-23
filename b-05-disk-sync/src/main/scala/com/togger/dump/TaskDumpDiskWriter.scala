package com.togger.dump

import com.togger.logging.TgLogging
import com.togger.project.Project
import com.togger.util.SplunkMacro.sp
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._

import java.util.UUID
import scala.util.Try

class TaskDumpDiskWriter(diskOps: DiskOps) extends TgLogging {

  def write(basePath: String, manifest: ProjectDumpManifest, tasks: List[TaskDumpData]): String = {
    val rootFolder = diskOps.resolvePath(basePath, projectFolderName(manifest.project))
    logger.info(s"Writing task dump to ${sp(rootFolder)}")

    diskOps.overwriteFileToDisk(rootFolder / "project.json", manifest.asJson.spaces2)
    tasks.foreach { taskDump =>
      diskOps.overwriteFileToDisk(rootFolder / s"${taskDump.task.id}.json", taskDump.asJson.spaces2)
    }

    logger.info(s"Task dump written to ${sp(rootFolder)} with ${sp(tasks.size)} tasks")
    rootFolder.toString
  }

  def readDumpedTasks(basePath: String, project: Project): Map[UUID, Either[String, TaskDumpData]] = {
    val rootFolder = diskOps.resolvePath(basePath, projectFolderName(project))
    diskOps
      .listFiles(rootFolder)
      .flatMap { file =>
        Try(UUID.fromString(file.baseName)).toOption.map(taskId => taskId -> parseTaskDumpFile(file))
      }
      .toMap
  }

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

}
