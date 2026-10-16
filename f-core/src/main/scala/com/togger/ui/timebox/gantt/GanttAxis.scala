package com.togger.ui.timebox.gantt

import java.time.{DayOfWeek, LocalDate}

/** Where a date landed on the axis compared to the visible columns. */
sealed trait GanttClamp

object GanttClamp {
  case object Inside extends GanttClamp
  case object Before extends GanttClamp
  case object After extends GanttClamp
}

case class ClampedColumn(index: Int, clamp: GanttClamp)

/** The x axis of the Gantt chart: one column per work day of the timebox, weekends and other days off left out. */
case class GanttAxis(days: Vector[LocalDate]) {

  def size: Int = days.size

  def isEmpty: Boolean = days.isEmpty

  def dateAt(index: Int): Option[LocalDate] = days.lift(index)

  /** The column of a date, only when that date is itself a visible work day. */
  def indexOf(date: LocalDate): Option[Int] = Some(days.indexOf(date)).filter(_ >= 0)

  /** The first column on or after the date, so a start on a day off begins on the next work day. */
  def firstIndexOnOrAfter(date: LocalDate): Option[Int] = Some(days.indexWhere(!_.isBefore(date))).filter(_ >= 0)

  /** The last column on or before the date, so a due date on a day off ends on the previous work day. */
  def lastIndexOnOrBefore(date: LocalDate): Option[Int] = Some(days.lastIndexWhere(!_.isAfter(date))).filter(_ >= 0)

  /** The column closest to the date (the earlier one on a tie), and whether the date lay outside the visible days. */
  def clampIndex(date: LocalDate): Option[ClampedColumn] = {
    if (isEmpty) return None
    if (date.isBefore(days.head)) return Some(ClampedColumn(0, GanttClamp.Before))
    if (date.isAfter(days.last)) return Some(ClampedColumn(size - 1, GanttClamp.After))
    val before = lastIndexOnOrBefore(date).get
    val after = firstIndexOnOrAfter(date).get
    val nearest = if (days(after).toEpochDay - date.toEpochDay < date.toEpochDay - days(before).toEpochDay) after else before
    Some(ClampedColumn(nearest, GanttClamp.Inside))
  }
}

object GanttAxis {

  val empty: GanttAxis = GanttAxis(Vector.empty)

  /** Every date from start to end, both included, that falls on a work day. A reversed range has no columns. */
  def of(start: LocalDate, end: LocalDate, workDays: Set[DayOfWeek]): GanttAxis =
    GanttAxis(
      Iterator
        .iterate(start)(_.plusDays(1))
        .takeWhile(!_.isAfter(end))
        .filter(day => workDays.contains(day.getDayOfWeek))
        .toVector
    )
}
