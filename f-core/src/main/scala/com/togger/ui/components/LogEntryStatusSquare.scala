package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.logentry.LogEntryStatus

/** The square that shows the status of the log entry of a day, or an empty one when there is none. */
object LogEntryStatusSquare {

  def apply(status: Option[LogEntryStatus]): HtmlElement =
    status match {
      case Some(s) =>
        div(
          title(s.toString),
          cls("w-5.5 h-5.5 rounded-md"),
          styleAttr := s"background-color: ${s.color};"
        )
      case None =>
        div(cls("w-5.5 h-5.5 rounded-md bg-neutral-quaternary-medium"))
    }
}
