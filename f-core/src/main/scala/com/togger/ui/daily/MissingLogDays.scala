package com.togger.ui.daily

import java.time.{DayOfWeek, LocalDate}

/** The work days a task has no log entry for, from the day after its last entry up to and including today. */
object MissingLogDays {

  /** A task with no entry at all is not asked about months of days, only about the latest ones. */
  val MaxDays = 31

  /** Oldest first. Without a last entry the days start at `taskStart`, and at today if that is still to come. */
  def of(
    lastLogDate: Option[LocalDate],
    taskStart: LocalDate,
    today: LocalDate,
    workDays: Set[DayOfWeek]
  ): Seq[LocalDate] = {
    val first = lastLogDate.fold(if (taskStart.isAfter(today)) today else taskStart)(_.plusDays(1))
    if (first.isAfter(today)) return Seq.empty
    Iterator
      .iterate(first)(_.plusDays(1))
      .takeWhile(!_.isAfter(today))
      .filter(day => workDays.contains(day.getDayOfWeek))
      .toSeq
      .takeRight(MaxDays)
  }

  def inFillOrder(days: Seq[LocalDate], todayFirst: Boolean): Seq[LocalDate] =
    if (todayFirst) days.reverse else days
}
