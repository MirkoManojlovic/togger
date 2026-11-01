package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindTaskRespFull
import com.togger.ui.task.DateEmoji
import com.togger.ui.task.TgDateFormater

import java.time.{LocalDate, YearMonth}

/** What the Gantt charts have in common: the grid, the header, the day columns, the bar and the labels. */
class GanttChartLayout(tgDateFormater: TgDateFormater, tgClock: TgClock) {

  private val NameColumn = "20rem"
  private val NameColumnRem = 20
  private val DefaultNameColumnTitle = "Task"

  def gridTemplate(axis: GanttAxis): String =
    s"grid-template-columns: $NameColumn repeat(${axis.size}, minmax(2.25rem, 1fr))"

  def minWidthStyle(axis: GanttAxis): String = s"min-width: ${NameColumnRem + axis.size * 2.25}rem"

  /** A column starts a new block when the day before it is not the previous column, so a weekend shows as a gap. */
  private def startsAfterGap(axis: GanttAxis, index: Int): Boolean =
    index > 0 && axis.days(index - 1).plusDays(1) != axis.days(index)

  def dayCellClasses(axis: GanttAxis, index: Int): String = {
    val gap = if (startsAfterGap(axis, index)) " border-l-2 border-l-default-strong" else " border-l"
    val today = if (axis.days(index) == tgClock.currentDate()) " bg-brand-softer" else ""
    s"border-default-medium$gap$today"
  }

  /** Consecutive columns of the same month, as (first day, number of columns). */
  private def monthSpans(axis: GanttAxis): Seq[(LocalDate, Int)] =
    axis.days
      .foldLeft(Vector.empty[(LocalDate, Int)]) { case (spans, day) =>
        spans.lastOption match {
          case Some((first, count)) if YearMonth.from(first) == YearMonth.from(day) =>
            spans.init :+ ((first, count + 1))
          case _ => spans :+ ((day, 1))
        }
      }

  private def monthRow(axis: GanttAxis): HtmlElement =
    div(
      cls("grid border-b border-default-medium text-center text-xs text-body-subtle"),
      styleAttr := gridTemplate(axis),
      div(),
      monthSpans(axis).map { case (first, count) =>
        div(
          cls("flex items-center justify-center border-l border-default-medium py-1 font-medium text-heading"),
          styleAttr := s"grid-column: span $count",
          tgDateFormater.monthName(first)
        )
      }
    )

  private def dayRow(axis: GanttAxis, nameColumnTitle: String): HtmlElement =
    div(
      cls("grid border-b border-default-medium text-center text-xs text-body-subtle"),
      styleAttr := gridTemplate(axis),
      div(cls("flex items-center justify-center px-2 py-1 font-medium"), nameColumnTitle),
      axis.days.zipWithIndex.map { case (day, index) =>
        div(
          cls(s"flex flex-col items-center justify-center py-1 ${dayCellClasses(axis, index)}"),
          div(tgDateFormater.dayOfWeekAbbrev(day)),
          div(cls("text-heading"), tgDateFormater.dayOnly(day))
        )
      }
    )

  def headerRow(axis: GanttAxis, nameColumnTitle: String = DefaultNameColumnTitle): HtmlElement =
    div(monthRow(axis), dayRow(axis, nameColumnTitle))

  def barTitle(task: FindTaskRespFull, start: LocalDate, due: LocalDate): String =
    barTitle(task.name, start, due)

  def barTitle(name: String, start: LocalDate, due: LocalDate): String =
    s"$name: ${tgDateFormater.asAmericanDate(start)} - ${tgDateFormater.asAmericanDate(due)}"

  def barStyle(placed: GanttBar.Placed): String =
    s"grid-column: ${placed.startCol + 2} / span ${placed.width}; grid-row: 1"

  def outsideStyle(axis: GanttAxis): String = s"grid-column: 2 / span ${axis.size}; grid-row: 1"

  /** The bar of a task without anything to grab it by. */
  def barElement(task: FindTaskRespFull, placed: GanttBar.Placed, memberIds: Seq[java.util.UUID]): HtmlElement =
    barElement(
      barTitle(task, task.startDate, task.dueDate),
      GanttAssigneeColors.classFor(task.assignedTo.map(_.id), memberIds),
      placed
    )

  /** A bar with the given tooltip and background colour class. */
  def barElement(tooltip: String, color: String, placed: GanttBar.Placed): HtmlElement = {
    val rounding = (placed.clippedLeft, placed.clippedRight) match {
      case (false, false) => "rounded-md"
      case (true, false)  => "rounded-r-md"
      case (false, true)  => "rounded-l-md"
      case (true, true)   => ""
    }
    div(
      cls(s"relative z-10 mx-1 my-2 flex h-9 items-center justify-between overflow-hidden $color px-1 text-xs text-white $rounding"),
      title := tooltip,
      span(if (placed.clippedLeft) "◂" else ""),
      span(if (placed.clippedRight) "▸" else "")
    )
  }

  def outsideElement(side: GanttClamp, rangeName: String = "timebox"): HtmlElement = {
    val (label, alignment) = side match {
      case GanttClamp.After => (s"starts after the $rangeName ▸", "justify-self-end")
      case _                => (s"◂ ends before the $rangeName", "justify-self-start")
    }
    span(
      cls(s"z-10 my-1.5 whitespace-nowrap px-1 text-xs italic text-body-subtle $alignment"),
      label
    )
  }

  /** The day columns behind the bar, so the columns show through. They cover the given grid rows. */
  def dayCells(axis: GanttAxis, rows: String): Seq[HtmlElement] =
    axis.days.indices.map(index =>
      div(cls(s"h-full ${dayCellClasses(axis, index)}"), styleAttr := s"grid-column: ${index + 2}; grid-row: $rows")
    )

  /** The bar of the task on its row, or the label for a task that lies outside the days. */
  def barOrOutside(
    task: FindTaskRespFull,
    axis: GanttAxis,
    bar: (FindTaskRespFull, GanttBar.Placed) => HtmlElement
  ): Modifier[HtmlElement] =
    barOrOutside(task.startDate, task.dueDate, axis, "timebox", placed => bar(task, placed))

  /** The bar for the dates on the row, or the label for dates that lie outside the days of the named range. */
  def barOrOutside(
    start: LocalDate,
    due: LocalDate,
    axis: GanttAxis,
    rangeName: String,
    bar: GanttBar.Placed => HtmlElement
  ): Modifier[HtmlElement] =
    GanttBar.of(start, due, axis).fold[Modifier[HtmlElement]](emptyMod) {
      case placed: GanttBar.Placed => bar(placed).amend(styleAttr := barStyle(placed))
      case GanttBar.Outside(side)  => outsideElement(side, rangeName).amend(styleAttr := outsideStyle(axis))
    }

  def emptyMessage(text: String): HtmlElement = p(cls("p-3 text-sm text-body-subtle"), text)

  /** The scrolling frame with the header and then the rows. */
  def frame(axis: GanttAxis, rows: Seq[HtmlElement], nameColumnTitle: String = DefaultNameColumnTitle): HtmlElement =
    div(
      cls("overflow-x-auto rounded-base border border-default-medium"),
      div(styleAttr := minWidthStyle(axis), headerRow(axis, nameColumnTitle), rows)
    )
}
