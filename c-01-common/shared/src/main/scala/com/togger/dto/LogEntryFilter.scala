package com.togger.dto

import java.time.LocalDate

/** Optional range on the log date of a log entry: `from` and `to` are both inclusive. */
case class LogEntryFilter(from: Option[LocalDate] = None, to: Option[LocalDate] = None)

object LogEntryFilter {
  val empty: LogEntryFilter = LogEntryFilter()
}
