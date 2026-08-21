package com.togger.dump

import com.togger.dump.DiskOps.homePrefix
import com.togger.logging.TgLogging

class DiskOps extends TgLogging {

  def overwriteFileToDisk(filePath: os.Path, contents: String): Unit =
    os.write.over(filePath, contents, createFolders = true)

  def readFileFromDisk(filePath: os.Path): String =
    os.read(filePath)

  def listFiles(folderPath: os.Path): Seq[os.Path] =
    if (os.exists(folderPath)) os.list(folderPath) else Seq.empty

  def resolvePath(basePath: String, segments: String*): os.Path = {

    segments.foldLeft(os.Path(processHomePath(basePath), os.home))(_ / _)
  }

  private def processHomePath(basePath: String): String =
    if (basePath.startsWith(homePrefix))
      basePath.stripPrefix(homePrefix)
    else
      basePath

}

object DiskOps {
  val homePrefix = "~/"

}
