package com.togger.dump

import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp
import io.circe.generic.auto._
import io.circe.syntax._

class TaskDumpDiskWriter(diskOps: DiskOps) extends TgLogging {

  def write(basePath: String, manifest: ProjectDumpManifest, tasks: List[TaskDumpData]): String = {
    val rootFolder = diskOps.resolvePath(basePath, projectFolderName(manifest))
    logger.info(s"Writing task dump to ${sp(rootFolder)}")

    diskOps.overwriteFileToDisk(rootFolder / "project.json", manifest.asJson.spaces2)
    tasks.foreach { taskDump =>
      diskOps.overwriteFileToDisk(rootFolder / s"${taskDump.task.id}.json", taskDump.asJson.spaces2)
    }

    logger.info(s"Task dump written to ${sp(rootFolder)} with ${sp(tasks.size)} tasks")
    rootFolder.toString
  }

  private def projectFolderName(manifest: ProjectDumpManifest): String = {
    val sanitizedName = manifest.project.name.replaceAll("[^A-Za-z0-9-_]", "-")
    s"${sanitizedName}_${manifest.project.id}"
  }

}
