package com.togger.ui.timebox.gantt

import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

object GanttLogStatus {

  def toLocalDate(instant: Instant): LocalDate = instant.atOffset(ZoneOffset.UTC).toLocalDate

  /** The status of each task on each day it has log entries. With several entries on a day the latest one counts. */
  def byTaskAndDay(entries: Seq[LogEntryDto]): Map[(UUID, LocalDate), LogEntryStatus] =
    entries
      .groupBy(entry => (entry.taskId, toLocalDate(entry.createdAt)))
      .map { case (key, dayEntries) => key -> dayEntries.maxBy(_.createdAt).status }
}
