package com.togger.controllers.taskdumpconfig

import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp

import scala.util.Try

sealed trait TaskDumpConfigError {
  def message: String
}

case class InvalidPath(dumpPath: String) extends TaskDumpConfigError {
  override def message: String = s"Invalid dump path $dumpPath. Path must be an absolute path."
}

class TaskDumpConfigVerification extends TgLogging {

  def verify(dumpPath: String): Either[TaskDumpConfigError, String] = {
    Try(os.Path(dumpPath))
      .fold(
        error => {
          logger.error(s"Invalid dump path provided ${sp(dumpPath)}")
          Left(InvalidPath(dumpPath))
        },
        validPath => {
          logger.info(s"Valid path: $dumpPath")
          Right(dumpPath)
        }
      )
  }
}
