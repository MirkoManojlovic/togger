package com.togger.ui.timebox.gantt

import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.UUID

object GanttLogStatus {

  def toLocalDate(instant: Instant): LocalDate = instant.atOffset(ZoneOffset.UTC).toLocalDate

  /** The log entry of each task on each day it has any. With several entries on a day the latest one counts. */
  def latestByTaskAndDay(entries: Seq[LogEntryDto]): Map[(UUID, LocalDate), LogEntryDto] =
    entries
      .groupBy(entry => (entry.taskId, toLocalDate(entry.createdAt)))
      .map { case (key, dayEntries) => key -> dayEntries.maxBy(_.createdAt) }

  def byTaskAndDay(entries: Seq[LogEntryDto]): Map[(UUID, LocalDate), LogEntryStatus] =
    latestByTaskAndDay(entries).map { case (key, entry) => key -> entry.status }
}
