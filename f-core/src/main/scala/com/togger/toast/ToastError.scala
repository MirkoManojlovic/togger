package com.togger.toast

import com.togger.common._
import com.togger.logging.TgLogging
import com.togger.util.SplunkMacro.sp

object ToastError extends TgLogging {
  def errorMatch(failure: TgJsonFailure): String = {
    failure match {
      case NotFound => "Oops, not found"
      case BadRequest(errors) =>
        errors.foldLeft("")((acc, nextError) => acc + nextError.message)
      case ServerError(error) =>
        error.getOrElse("Oops, a server error occurred")
      case Unauthenticated =>
        "Session expired, please log in again"
      case Forbidden =>
        "Oops, you are not allowed to do that"
      case error @ _ =>
        logger.error(s"Error ${sp(error)}")
        "Unknown message occurred"
    }
  }
}
