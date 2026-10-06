package com.togger.common

import java.time.{DayOfWeek, LocalDate}

object WorkDayCalendar {

  val DefaultWorkDays: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  /** The given day, or the next one that is a work day. With no work days at all there is nothing to look for. */
  def firstWorkDayFrom(day: LocalDate, workDays: Set[DayOfWeek]): LocalDate = {
    if (workDays.isEmpty) return day
    Iterator.iterate(day)(_.plusDays(1)).find(d => workDays.contains(d.getDayOfWeek)).get
  }

  /** The next given weekday strictly after `from`, so a Monday asking for the next Monday gets a week later. */
  def nextWeekday(from: LocalDate, weekday: DayOfWeek): LocalDate = {
    val daysAhead = (weekday.getValue - from.getDayOfWeek.getValue + 7) % 7
    from.plusDays(if (daysAhead == 0) 7 else daysAhead.toLong)
  }

  /** The day on which `count` work days are done, counting the start day itself: from a Monday, 5 work days end on
    * Friday. A start that is not a work day begins counting on the next work day.
    */
  def lastDayOfWorkDays(start: LocalDate, count: Int, workDays: Set[DayOfWeek]): LocalDate = {
    val first = firstWorkDayFrom(start, workDays)
    if (workDays.isEmpty || count <= 1) return first
    Iterator
      .iterate(first)(_.plusDays(1))
      .filter(d => workDays.contains(d.getDayOfWeek))
      .drop(count - 1)
      .next()
  }

  /** Work days from `start` to `end`, counting both ends. */
  def countWorkDays(start: LocalDate, end: LocalDate, workDays: Set[DayOfWeek]): Int =
    Iterator
      .iterate(start)(_.plusDays(1))
      .takeWhile(!_.isAfter(end))
      .count(day => workDays.contains(day.getDayOfWeek))
}
