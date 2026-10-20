package com.togger.ui.slacker

import java.time.{DayOfWeek, LocalDate}

/** The work days from the start of a task through today that have no log entry, oldest first. */
object SlackerMissingDays {

  def of(
    loggedDays: Iterable[LocalDate],
    startDate: LocalDate,
    today: LocalDate,
    workDays: Set[DayOfWeek]
  ): Seq[LocalDate] = {
    val logged = loggedDays.toSet
    Iterator
      .iterate(startDate)(_.plusDays(1))
      .takeWhile(!_.isAfter(today))
      .filter(day => workDays.contains(day.getDayOfWeek) && !logged.contains(day))
      .toSeq
  }
}
