package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindTaskRespFull
import com.togger.ui.auth.TgCard
import com.togger.ui.components.reorder.{ListReorder, RowReorder}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.timebox.TimeboxStagingStore

import org.scalajs.dom

import scala.scalajs.js
import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The staged tasks as a Gantt chart: one column per work day of the timebox, one row per task, each task as a bar
  * from its start date to its due date.
  */
class TimeboxGanttComponent(
  stagingStore: TimeboxStagingStore,
  projectId: UUID,
  workDays: Signal[Set[DayOfWeek]],
  timebox: Signal[Option[(LocalDate, LocalDate)]],
  tgDateFormater: TgDateFormater,
  tgClock: TgClock
) {

  private val NameColumn = "15rem"
  private val NameColumnRem = 15

  /** The order the rows were arranged in. It is only for looking at the chart and is not saved. */
  private val rowOrder = Var(Seq.empty[UUID])

  private val orderedTasks: Signal[Seq[FindTaskRespFull]] =
    stagingStore.stagedWithDates(projectId).combineWith(rowOrder.signal).map { case (tasks, order) =>
      GanttRowOrder.ordered(tasks, order)
    }

  private def keysNow(): Seq[UUID] =
    GanttRowOrder.ordered(stagingStore.stagedWithDatesNow(projectId), rowOrder.now()).map(_.id)

  private val rowReorder = new RowReorder[UUID](
    orderedTasks.map(_.map(_.id)),
    keysNow,
    (from, insertion) => rowOrder.set(ListReorder.moveToInsertion(keysNow(), from, insertion))
  )

  private val axisSignal: Signal[GanttAxis] =
    timebox
      .combineWith(workDays)
      .map { case (range, days) => range.fold(GanttAxis.empty) { case (start, end) => GanttAxis.of(start, end, days) } }
      .distinct

  private def gridTemplate(axis: GanttAxis): String =
    s"grid-template-columns: $NameColumn repeat(${axis.size}, minmax(2.25rem, 1fr))"

  /** A column starts a new block when the day before it is not the previous column, so a weekend shows as a gap. */
  private def startsAfterGap(axis: GanttAxis, index: Int): Boolean =
    index > 0 && axis.days(index - 1).plusDays(1) != axis.days(index)

  private def dayCellClasses(axis: GanttAxis, index: Int): String = {
    val gap = if (startsAfterGap(axis, index)) " border-l-2 border-l-default-strong" else " border-l"
    val today = if (axis.days(index) == tgClock.currentDate()) " bg-neutral-tertiary-medium" else ""
    s"border-default-medium$gap$today"
  }

  private def headerRow(axis: GanttAxis): HtmlElement =
    div(
      cls("grid border-b border-default-medium text-center text-xs text-body-subtle"),
      styleAttr := gridTemplate(axis),
      div(cls("px-2 py-1 text-left font-medium"), "Task"),
      axis.days.zipWithIndex.map { case (day, index) =>
        val monthStarts = index == 0 || axis.days(index - 1).getMonth != day.getMonth
        div(
          cls(s"py-1 ${dayCellClasses(axis, index)}"),
          div(cls("h-4 font-medium text-heading"), if (monthStarts) tgDateFormater.monthName(day).take(3) else ""),
          div(tgDateFormater.dayOfWeekAbbrev(day)),
          div(cls("text-heading"), tgDateFormater.dayOnly(day))
        )
      }
    )

  private def barTitle(task: FindTaskRespFull, start: LocalDate, due: LocalDate): String =
    s"${task.name}: ${tgDateFormater.asAmericanDate(start)} - ${tgDateFormater.asAmericanDate(due)}"

  private def columnStyle(bar: GanttBar.Placed): String = s"${bar.startCol + 2} / span ${bar.width}"

  /** Where a drag of one edge began: the pointer, the width of a column and the bar as it was. */
  private case class Drag(startX: Double, columnWidth: Double, bar: GanttBar.Placed)

  /** A strip on one end of the bar to grab. While dragging only the bar's element is changed, so the grab is not lost
    * to a redraw; the new dates are saved once the pointer is let go.
    */
  private def resizeHandle(
    task: FindTaskRespFull,
    placed: GanttBar.Placed,
    axis: GanttAxis,
    edge: GanttEdge
  ): HtmlElement = {
    val drag = Var(Option.empty[Drag])
    val side = if (edge == GanttEdge.Start) "left-0" else "right-0"

    def barOf(event: dom.PointerEvent): dom.html.Element =
      event.currentTarget.asInstanceOf[dom.html.Element].parentElement.asInstanceOf[dom.html.Element]

    // The row is draggable for reordering, and the browser would start that drag from a press on the handle. The
    // drag starts from the press itself, so the row must stop being draggable before it, and is again once let go.
    def rowDraggable(event: dom.PointerEvent, draggable: Boolean): Unit =
      barOf(event).parentElement.setAttribute("draggable", draggable.toString)

    def resized(event: dom.PointerEvent, state: Drag): GanttBar.Placed = {
      val deltaCols = math.round((event.clientX - state.startX) / state.columnWidth).toInt
      GanttResize.resize(state.bar, edge, deltaCols, axis)
    }

    def show(bar: dom.html.Element, shown: GanttBar.Placed, start: LocalDate, due: LocalDate): Unit = {
      bar.style.setProperty("grid-column", columnStyle(shown))
      bar.title = barTitle(task, start, due)
    }

    def datesFor(shown: GanttBar.Placed): (LocalDate, LocalDate) =
      GanttResize.newDates(task.startDate, task.dueDate, edge, shown, axis).getOrElse((task.startDate, task.dueDate))

    div(
      cls(s"absolute top-0 $side h-full w-2 cursor-ew-resize bg-blue-700/60 hover:bg-blue-800"),
      styleAttr := "touch-action: none",
      onPointerDown --> { event =>
        event.stopPropagation()
        rowDraggable(event, draggable = false)
        event.currentTarget.asInstanceOf[js.Dynamic].setPointerCapture(event.pointerId): Unit
        val columnWidth = barOf(event).getBoundingClientRect().width / placed.width
        drag.set(Some(Drag(event.clientX, columnWidth, placed)))
      },
      onPointerMove --> { event =>
        drag.now().foreach { state =>
          val shown = resized(event, state)
          val (start, due) = datesFor(shown)
          show(barOf(event), shown, start, due)
        }
      },
      onPointerUp --> { event =>
        rowDraggable(event, draggable = true)
        drag.now().foreach { state =>
          drag.set(None)
          val (start, due) = datesFor(resized(event, state))
          if ((start, due) != ((task.startDate, task.dueDate))) stagingStore.setDates(projectId, task.id, start, due)
          else show(barOf(event), state.bar, task.startDate, task.dueDate)
        }
      },
      onPointerCancel --> { event =>
        rowDraggable(event, draggable = true)
        drag.now().foreach(state => show(barOf(event), state.bar, task.startDate, task.dueDate))
        drag.set(None)
      }
    )
  }

  private def barElement(task: FindTaskRespFull, placed: GanttBar.Placed, axis: GanttAxis): HtmlElement = {
    val rounding = (placed.clippedLeft, placed.clippedRight) match {
      case (false, false) => "rounded-base"
      case (true, false)  => "rounded-r-base"
      case (false, true)  => "rounded-l-base"
      case (true, true)   => ""
    }
    div(
      cls(s"relative z-10 my-1.5 flex h-6 items-center justify-between overflow-hidden bg-blue-500 px-1 text-xs text-white $rounding"),
      title := barTitle(task, task.startDate, task.dueDate),
      // A clipped end has its real date out of sight, so it can't be dragged from here.
      if (placed.clippedLeft) emptyNode else resizeHandle(task, placed, axis, GanttEdge.Start),
      span(if (placed.clippedLeft) "◂" else ""),
      span(if (placed.clippedRight) "▸" else ""),
      if (placed.clippedRight) emptyNode else resizeHandle(task, placed, axis, GanttEdge.End)
    )
  }

  private def outsideElement(side: GanttClamp): HtmlElement = {
    val (label, alignment) = side match {
      case GanttClamp.After => ("starts after the timebox ▸", "justify-self-end")
      case _                => ("◂ ends before the timebox", "justify-self-start")
    }
    span(
      cls(s"z-10 my-1.5 whitespace-nowrap px-1 text-xs italic text-body-subtle $alignment"),
      label
    )
  }

  private def taskRow(task: FindTaskRespFull, axis: GanttAxis): HtmlElement =
    div(
      cls("relative grid items-center border-b border-default-medium"),
      styleAttr := gridTemplate(axis),
      rowReorder.dragModifiers(task.id),
      div(
        cls("flex items-center gap-1 overflow-hidden px-1 py-1"),
        rowReorder.controls(task.id),
        span(cls("truncate text-sm text-heading"), title := task.name, task.name)
      ),
      // The day cells sit behind the bar so the columns show through.
      axis.days.indices.map(index =>
        div(cls(s"h-full ${dayCellClasses(axis, index)}"), styleAttr := s"grid-column: ${index + 2}; grid-row: 1")
      ),
      GanttBar.of(task.startDate, task.dueDate, axis).fold[Modifier[HtmlElement]](emptyMod) {
        case placed: GanttBar.Placed => barElement(task, placed, axis).amend(styleAttr := barStyle(placed))
        case GanttBar.Outside(side)  => outsideElement(side).amend(styleAttr := outsideStyle(axis))
      }
    )

  private def barStyle(placed: GanttBar.Placed): String =
    s"grid-column: ${placed.startCol + 2} / span ${placed.width}; grid-row: 1"

  private def outsideStyle(axis: GanttAxis): String = s"grid-column: 2 / span ${axis.size}; grid-row: 1"

  private def emptyMessage(text: String): HtmlElement = p(cls("p-3 text-sm text-body-subtle"), text)

  private def chart(axis: GanttAxis, tasks: Seq[FindTaskRespFull]): HtmlElement =
    if (axis.isEmpty) emptyMessage("Pick a valid start and end date with at least one work day to see the Gantt chart.")
    else if (tasks.isEmpty) emptyMessage("Stage some tasks to see them on the Gantt chart.")
    else
      div(
        cls("overflow-x-auto rounded-base border border-default-medium"),
        div(
          styleAttr := s"min-width: ${NameColumnRem + axis.size * 2.25}rem",
          headerRow(axis),
          tasks.map(taskRow(_, axis))
        )
      )

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Gantt chart", TgCard.CardHeaderSize.H5),
        body = TgCard.HtmlCardContent(
          div(child <-- axisSignal.combineWith(orderedTasks).map { case (axis, tasks) =>
            chart(axis, tasks)
          })
        )
      )
      .element
}
