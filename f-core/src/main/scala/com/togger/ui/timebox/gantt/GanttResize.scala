package com.togger.ui.timebox.gantt

import java.time.LocalDate

/** Which end of a bar is dragged. */
sealed trait GanttEdge

object GanttEdge {
  case object Start extends GanttEdge
  case object End extends GanttEdge
}

object GanttResize {

  /** The bar after dragging one edge by whole columns. An edge stays within the axis and can't pass the other one, so
    * a bar keeps at least one column.
    */
  def resize(bar: GanttBar.Placed, edge: GanttEdge, deltaCols: Int, axis: GanttAxis): GanttBar.Placed =
    edge match {
      case GanttEdge.Start => bar.copy(startCol = (bar.startCol + deltaCols).max(0).min(bar.endCol))
      case GanttEdge.End   => bar.copy(endCol = (bar.endCol + deltaCols).min(axis.size - 1).max(bar.startCol))
    }

  /** The task's dates after the resize. Only the dragged edge changes its date, to the date of its new column; the
    * other date is kept as it is, even when the bar shows it snapped to a work day or clipped. Nothing changes when
    * the result would end before it starts.
    */
  def newDates(
    start: LocalDate,
    due: LocalDate,
    edge: GanttEdge,
    resized: GanttBar.Placed,
    axis: GanttAxis
  ): Option[(LocalDate, LocalDate)] = {
    val dates = edge match {
      case GanttEdge.Start => axis.dateAt(resized.startCol).map((_, due))
      case GanttEdge.End   => axis.dateAt(resized.endCol).map((start, _))
    }
    dates.filter { case (newStart, newDue) => !newDue.isBefore(newStart) }
  }
}
