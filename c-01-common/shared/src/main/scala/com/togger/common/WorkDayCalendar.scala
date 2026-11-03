package com.togger.common

import java.time.DayOfWeek
import java.time.LocalDate

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

  /** Divides `start` to `end` into `count` consecutive date ranges of about the same number of work days, in order.
    * Every range lies inside `start` - `end`. With fewer work days than ranges, ranges share a day. With no work day in
    * the period (or no work days configured) the calendar days are divided instead.
    */
  def splitEvenly(
    start: LocalDate,
    end: LocalDate,
    count: Int,
    workDays: Set[DayOfWeek]
  ): List[(LocalDate, LocalDate)] = {
    if (count <= 0 || end.isBefore(start)) return Nil
    val allDays = Iterator.iterate(start)(_.plusDays(1)).takeWhile(!_.isAfter(end)).toVector
    val workDaysInPeriod = allDays.filter(day => workDays.contains(day.getDayOfWeek))
    val days = if (workDaysInPeriod.isEmpty) allDays else workDaysInPeriod
    (0 until count).toList.map { index =>
      val first = index * days.size / count
      val last = ((index + 1) * days.size / count - 1).max(first)
      days(first) -> days(last)
    }
  }

  /** Divides `start` to `end` into one consecutive date range per stage (see [[splitEvenly]]) and gives it to every
    * step of the stage, as the steps of a stage run in parallel. `stageSizes` holds the number of steps of each stage
    * in order; the result has one range per step, stage by stage. Empty when there is no stage or the period is
    * reversed.
    */
  def splitStages(
    start: LocalDate,
    end: LocalDate,
    stageSizes: List[Int],
    workDays: Set[DayOfWeek]
  ): List[(LocalDate, LocalDate)] =
    splitEvenly(start, end, stageSizes.size, workDays).zip(stageSizes).flatMap { case (range, size) =>
      List.fill(size)(range)
    }

  /** Work days from `start` to `end`, counting both ends. */
  def countWorkDays(start: LocalDate, end: LocalDate, workDays: Set[DayOfWeek]): Int =
    Iterator
      .iterate(start)(_.plusDays(1))
      .takeWhile(!_.isAfter(end))
      .count(day => workDays.contains(day.getDayOfWeek))
}
