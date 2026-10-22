package com.togger.dump

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._
import com.togger.common.TgError
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp

import scala.util.Try

sealed trait DataImportConfigError extends TgError

case class InvalidAbsolutePath(importPath: String) extends DataImportConfigError {
  override def message: String = s"Invalid dump path $importPath. Path must be an absolute path."
}

case class BlankName(name: String) extends DataImportConfigError {
  override def message: String = "Name must not be blank."
}

case class PathDoesNotExist(importPath: String) extends DataImportConfigError {
  override def message: String = s"Dump path $importPath does not exist."
}

class DataImportConfigVerification extends TgLogging {

  def verify(name: String, importPath: String): EitherNec[TgError, (String, String)] =
    (
      verifyName(name),
      verifyPaths(importPath)
    ).mapN((_, _) => name -> importPath).toEither

  private def verifyName(name: String): ValidatedNec[TgError, String] =
    if (name.trim.nonEmpty) {
      name.validNec
    } else {
      logger.error(s"Blank name provided ${sp(name)}")
      BlankName(name).invalidNec
    }

  private def verifyPaths(importPath: String): ValidatedNec[TgError, String] =
    verifyAbsolutePath(importPath).fold(
      errors => errors.invalid[String],
      importPath => verifyPathExists(importPath)
    )

  private def verifyAbsolutePath(importPath: String): ValidatedNec[TgError, String] =
    Try(os.Path(importPath))
      .fold(
        _ => {
          logger.error(s"Invalid dump path provided ${sp(importPath)}")
          InvalidAbsolutePath(importPath).invalidNec
        },
        _ => {
          logger.info(s"Valid path: $importPath")
          importPath.validNec
        }
      )

  private def verifyPathExists(importPath: String): ValidatedNec[TgError, String] =
    if (os.exists(os.Path(importPath))) {
      logger.info(s"Path exists: $importPath")
      importPath.validNec
    } else {
      logger.error(s"Dump path does not exist ${sp(importPath)}")
      PathDoesNotExist(importPath).invalidNec
    }

}
