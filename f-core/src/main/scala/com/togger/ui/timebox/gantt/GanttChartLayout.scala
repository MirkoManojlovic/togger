package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindTaskRespFull
import com.togger.ui.task.DateEmoji
import com.togger.ui.task.TgDateFormater

import org.scalajs.dom

import java.time.{LocalDate, YearMonth}

/** What the Gantt charts have in common: the grid, the header, the day columns, the bar and the labels. */
class GanttChartLayout(tgDateFormater: TgDateFormater, tgClock: TgClock) {

  private val NameColumnVar = "--gantt-name-col"
  private val MinNameColumnPx = 192.0 // 12rem at default root font size
  private val MaxNameColumnPx = 384.0 // 24rem at default root font size
  private val DefaultNameColumnTitle = "Task"

  /** None while the column is sized by its widest cell, so that the width can be measured. */
  private val nameColumnWidthPxVar: Var[Option[Double]] = Var(None)
  private val nameColumnDragVar: Var[Option[(Double, Double)]] = Var(None) // (startClientX, startWidth)

  def gridTemplate(axis: GanttAxis): String =
    s"grid-template-columns: var($NameColumnVar) repeat(${axis.size}, minmax(2.25rem, 1fr))"

  def minWidthStyle(axis: GanttAxis): String = s"min-width: calc(var($NameColumnVar) + ${axis.size * 2.25}rem)"

  /** The name cell of a row. It spans the whole row so that, being sticky, it stays in view while the days scroll. */
  val nameCellClasses = "gantt-name-cell sticky left-0 z-20 border-r border-default-medium bg-neutral-primary-soft"

  def nameCellStyle(gridRow: String = "1"): String =
    s"grid-column: 1 / -1; grid-row: $gridRow; justify-self: start; width: var($NameColumnVar)"

  private def startNameColumnDrag(event: dom.MouseEvent): Unit = {
    event.preventDefault()
    nameColumnDragVar.set(Some((event.clientX, nameColumnWidthPxVar.now().getOrElse(MinNameColumnPx))))
  }

  private def moveNameColumnDrag(event: dom.MouseEvent): Unit =
    nameColumnDragVar.now().foreach { case (startX, startWidth) =>
      nameColumnWidthPxVar.set(Some(clampNameColumn(startWidth + event.clientX - startX)))
    }

  private def clampNameColumn(width: Double): Double = width.max(MinNameColumnPx).min(MaxNameColumnPx)

  /** Sizes the column to its widest cell, header and rows alike, within the minimum and the maximum. This is done once:
    * a chart redrawn after an edit keeps the width it has, as does a width the user dragged to.
    */
  private def fitNameColumn(frameElement: dom.Element): Unit =
    if (nameColumnWidthPxVar.now().isEmpty) {
      dom.window.requestAnimationFrame { _ =>
        val cells = frameElement.querySelectorAll(".gantt-name-cell")
        val widest = (0 until cells.length).map(cells(_).asInstanceOf[dom.html.Element].offsetWidth).maxOption
        nameColumnWidthPxVar.set(Some(clampNameColumn(widest.getOrElse(MinNameColumnPx))))
      }
    }

  /** The grip in the middle of the name column's right edge. The strip around it is a sticky, zero wide flex item as
    * tall as the chart, so the grip stays in view and vertically centered while the days scroll.
    */
  private val nameColumnResizeHandle: HtmlElement =
    div(
      cls("sticky left-0 z-40 w-0 self-stretch"),
      div(
        cls("pointer-events-none absolute inset-y-0 left-0 flex items-center justify-end"),
        styleAttr := s"width: var($NameColumnVar)",
        div(
          cls("pointer-events-auto h-20 w-2 cursor-col-resize rounded-full bg-neutral-quaternary-medium hover:bg-fg-brand"),
          cls("bg-fg-brand") <-- nameColumnDragVar.signal.map(_.isDefined),
          onMouseDown --> (event => startNameColumnDrag(event))
        )
      )
    )

  /** A column starts a new block when the day before it is not the previous column, so a weekend shows as a gap. */
  private def startsAfterGap(axis: GanttAxis, index: Int): Boolean =
    index > 0 && axis.days(index - 1).plusDays(1) != axis.days(index)

  def dayCellClasses(axis: GanttAxis, index: Int): String = {
    val gap = if (startsAfterGap(axis, index)) " border-l-2 border-l-default-strong" else " border-l"
    val today = if (axis.days(index) == tgClock.currentDate()) " bg-brand-softer" else ""
    s"border-default-medium$gap$today"
  }


  /** Consecutive columns of the same month, as (first day, first column index, number of columns). */
  private def monthSpans(axis: GanttAxis): Seq[(LocalDate, Int, Int)] =
    axis.days.zipWithIndex
      .foldLeft(Vector.empty[(LocalDate, Int, Int)]) { case (spans, (day, index)) =>
        spans.lastOption match {
          case Some((first, start, count)) if YearMonth.from(first) == YearMonth.from(day) =>
            spans.init :+ ((first, start, count + 1))
          case _ => spans :+ ((day, index, 1))
        }
      }

  /** The name column cell spans both header rows; the months row and the days row start from the second column. */
  def headerRow(axis: GanttAxis, nameColumnTitle: String = DefaultNameColumnTitle): HtmlElement =
    div(
      cls("grid border-b border-default-medium text-center text-xs text-body-subtle"),
      styleAttr := gridTemplate(axis),
      div(
        cls(s"flex items-center justify-center px-2 py-1 font-medium $nameCellClasses z-30"),
        styleAttr := nameCellStyle("1 / span 2"),
        nameColumnTitle
      ),
      monthSpans(axis).map { case (first, start, count) =>
        div(
          cls("flex items-center justify-center border-b border-l border-default-medium py-1 font-medium text-heading"),
          styleAttr := s"grid-column: ${start + 2} / span $count; grid-row: 1",
          tgDateFormater.monthName(first)
        )
      },
      axis.days.zipWithIndex.map { case (day, index) =>
        div(
          cls(s"flex flex-col items-center justify-center py-1 ${dayCellClasses(axis, index)}"),
          styleAttr := s"grid-column: ${index + 2}; grid-row: 2",
          div(tgDateFormater.dayOfWeekAbbrev(day)),
          div(cls("text-heading"), tgDateFormater.dayOnly(day))
        )
      }
    )

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
      cls(s"relative z-10 mx-1 my-1 flex h-7 items-center justify-between overflow-hidden $color px-1 text-xs text-white $rounding"),
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
      cls(s"z-10 my-1 whitespace-nowrap px-1 text-xs italic text-body-subtle $alignment"),
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
      styleProp[String](NameColumnVar) <-- nameColumnWidthPxVar.signal.map(_.fold("max-content")(width => s"${width}px")),
      onMountCallback(context => fitNameColumn(context.thisNode.ref)),
      windowEvents(_.onMouseMove) --> (event => moveNameColumnDrag(event)),
      windowEvents(_.onMouseUp) --> (_ => nameColumnDragVar.set(None)),
      div(
        cls("flex"),
        styleAttr := minWidthStyle(axis),
        nameColumnResizeHandle,
        div(cls("min-w-0 flex-1"), headerRow(axis, nameColumnTitle), rows)
      )
    )
}
