package com.togger.logging

import cats.effect.IO
import scribe.Logging
import scribe.cats._
import scribe.mdc.MDC

trait TgLogging extends Logging with TgLoggingExtra

trait TgLoggingExtra { this: Logging =>

  def logAroundInfo[A](codeBlock: => A)(msgBefore: String, msgToLogAfter: A => String)(implicit
    pkg: sourcecode.Pkg,
    fileName: sourcecode.FileName,
    name: sourcecode.Name,
    line: sourcecode.Line,
    mdc: MDC
  ): A = {
    logger.info(msgBefore)
    val result = codeBlock
    logger.info(msgToLogAfter(result))
    result
  }

  def logAroundInfoIO[A](codeBlock: => IO[A])(msgBefore: String, msgToLogAfter: A => String)(implicit
    pkg: sourcecode.Pkg,
    fileName: sourcecode.FileName,
    name: sourcecode.Name,
    line: sourcecode.Line,
    mdc: MDC
  ): IO[A] =
    logger.f[IO].info(msgBefore) >> codeBlock.map { result =>
      logger.info(msgToLogAfter(result))
      result
    }

}
