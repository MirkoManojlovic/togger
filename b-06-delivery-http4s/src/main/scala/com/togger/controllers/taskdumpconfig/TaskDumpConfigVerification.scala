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

case class BlankName(name: String) extends TaskDumpConfigError {
  override def message: String = "Name must not be blank."
}

class TaskDumpConfigVerification extends TgLogging {

  def verify(name: String, dumpPath: String): Either[TaskDumpConfigError, (String, String)] =
    verifyName(name).flatMap(validName => verifyPath(dumpPath).map(validPath => (validName, validPath)))

  private def verifyName(name: String): Either[TaskDumpConfigError, String] =
    if (name.trim.nonEmpty) {
      Right(name)
    } else {
      logger.error(s"Blank name provided ${sp(name)}")
      Left(BlankName(name))
    }

  private def verifyPath(dumpPath: String): Either[TaskDumpConfigError, String] =
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
