package com.togger.dto

import java.time.Instant

/** Optional date range on the creation time of a log entry: `from` is inclusive, `to` is exclusive. */
case class LogEntryFilter(from: Option[Instant] = None, to: Option[Instant] = None)

object LogEntryFilter {
  val empty: LogEntryFilter = LogEntryFilter()
}
