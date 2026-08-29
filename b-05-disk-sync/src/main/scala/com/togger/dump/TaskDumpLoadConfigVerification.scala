package com.togger.dump

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._
import com.togger.common.TgError
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp

import scala.util.Try

sealed trait TaskDumpLoadConfigError extends TgError

case class InvalidAbsolutePath(dumpPath: String) extends TaskDumpLoadConfigError {
  override def message: String = s"Invalid dump path $dumpPath. Path must be an absolute path."
}

case class BlankName(name: String) extends TaskDumpLoadConfigError {
  override def message: String = "Name must not be blank."
}

case class PathDoesNotExist(dumpPath: String) extends TaskDumpLoadConfigError {
  override def message: String = s"Dump path $dumpPath does not exist."
}

class TaskDumpLoadConfigVerification extends TgLogging {

  def verify(name: String, dumpPath: String): EitherNec[TgError, (String, String)] =
    (
      verifyName(name),
      verifyPaths(dumpPath)
    ).mapN((_, _) => name -> dumpPath).toEither

  private def verifyName(name: String): ValidatedNec[TgError, String] =
    if (name.trim.nonEmpty) {
      name.validNec
    } else {
      logger.error(s"Blank name provided ${sp(name)}")
      BlankName(name).invalidNec
    }

  private def verifyPaths(dumpPath: String): ValidatedNec[TgError, String] =
    verifyAbsolutePath(dumpPath).fold(
      errors => errors.invalid[String],
      dumpPath => verifyPathExists(dumpPath)
    )

  private def verifyAbsolutePath(dumpPath: String): ValidatedNec[TgError, String] =
    Try(os.Path(dumpPath))
      .fold(
        _ => {
          logger.error(s"Invalid dump path provided ${sp(dumpPath)}")
          InvalidAbsolutePath(dumpPath).invalidNec
        },
        _ => {
          logger.info(s"Valid path: $dumpPath")
          dumpPath.validNec
        }
      )

  private def verifyPathExists(dumpPath: String): ValidatedNec[TgError, String] =
    if (os.exists(os.Path(dumpPath))) {
      logger.info(s"Path exists: $dumpPath")
      dumpPath.validNec
    } else {
      logger.error(s"Dump path does not exist ${sp(dumpPath)}")
      PathDoesNotExist(dumpPath).invalidNec
    }

}
