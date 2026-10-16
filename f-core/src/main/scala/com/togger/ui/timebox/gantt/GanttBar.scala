package com.togger.ui.timebox.gantt

import java.time.LocalDate

/** Where a task sits on the Gantt axis. */
sealed trait GanttBar

object GanttBar {

  /** A bar over the columns `startCol` to `endCol`, both included. The clipped flags say the task goes on past the
    * visible days on that side.
    */
  case class Placed(startCol: Int, endCol: Int, clippedLeft: Boolean, clippedRight: Boolean) extends GanttBar {
    def width: Int = endCol - startCol + 1
  }

  /** The task lies completely before or after the visible days. */
  case class Outside(side: GanttClamp) extends GanttBar

  /** A start on a day off begins on the next work day and a due date on a day off ends on the previous one. A task
    * that lies only on days off between two columns still gets its one column. An axis without columns has no bars.
    */
  def of(start: LocalDate, due: LocalDate, axis: GanttAxis): Option[GanttBar] = {
    if (axis.isEmpty) return None
    val (first, last) = (axis.days.head, axis.days.last)
    if (due.isBefore(first)) return Some(Outside(GanttClamp.Before))
    if (start.isAfter(last)) return Some(Outside(GanttClamp.After))
    val startCol = axis.firstIndexOnOrAfter(start).getOrElse(0)
    val endCol = axis.lastIndexOnOrBefore(due).getOrElse(axis.size - 1).max(startCol)
    Some(Placed(startCol, endCol, clippedLeft = start.isBefore(first), clippedRight = due.isAfter(last)))
  }
}
