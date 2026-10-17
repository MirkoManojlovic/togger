package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.FindTaskRespFull
import com.togger.ui.auth.TgCard
import com.togger.ui.components.reorder.{ListReorder, RowReorder}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.task.bulk.BulkLookups
import com.togger.ui.timebox.{StagedTaskBadges, TimeboxStagingStore}

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
  tgClock: TgClock,
  lookups: Signal[BulkLookups],
  taskBadges: FindTaskRespFull => StagedTaskBadges
) {

  private val NameColumn = "20rem"
  private val NameColumnRem = 20

  /** The order the rows were arranged in. It is only for looking at the chart and is not saved. */
  private val rowOrder = Var(Seq.empty[UUID])

  /** The members of the project, kept here so the rows can be put in order by assignee. */
  private val memberIds = Var(Seq.empty[UUID])

  private val orderedTasks: Signal[Seq[FindTaskRespFull]] =
    stagingStore
      .stagedWithDates(projectId)
      .combineWith(rowOrder.signal, memberIds.signal)
      .map { case (tasks, order, members) => GanttRowOrder.ordered(tasks, order, members) }

  private def keysNow(): Seq[UUID] =
    GanttRowOrder.ordered(stagingStore.stagedWithDatesNow(projectId), rowOrder.now(), memberIds.now()).map(_.id)

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
      div(cls("flex items-center justify-center px-2 py-1 font-medium"), "Task"),
      axis.days.zipWithIndex.map { case (day, index) =>
        val monthStarts = index == 0 || axis.days(index - 1).getMonth != day.getMonth
        div(
          cls(s"flex flex-col items-center justify-center py-1 ${dayCellClasses(axis, index)}"),
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

  /** A strip on one end of the bar to grab, to resize it. */
  private def resizeHandle(
    task: FindTaskRespFull,
    placed: GanttBar.Placed,
    axis: GanttAxis,
    edge: GanttEdge
  ): HtmlElement = {
    val side = if (edge == GanttEdge.Start) "left-0" else "right-0"
    div(
      cls(s"absolute top-0 $side h-full w-2 cursor-ew-resize hover:bg-white/30"),
      dragModifiers(task, placed, axis, edge)
    )
  }

  /** What to put on an element to drag the bar by it, on its end to resize or on the whole of it to move. While
    * dragging only the bar's element is changed, so the grab is not lost to a redraw; the new dates are saved once the
    * pointer is let go.
    */
  private def dragModifiers(
    task: FindTaskRespFull,
    placed: GanttBar.Placed,
    axis: GanttAxis,
    edge: GanttEdge
  ): Seq[Modifier[HtmlElement]] = {
    val drag = Var(Option.empty[Drag])

    def barOf(event: dom.PointerEvent): dom.html.Element = {
      val grabbed = event.currentTarget.asInstanceOf[dom.html.Element]
      if (edge == GanttEdge.Body) grabbed else grabbed.parentElement.asInstanceOf[dom.html.Element]
    }

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

    Seq(
      styleAttr := "touch-action: none",
      onPointerDown --> { event =>
        event.stopPropagation()
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
        drag.now().foreach { state =>
          drag.set(None)
          val (start, due) = datesFor(resized(event, state))
          if ((start, due) != ((task.startDate, task.dueDate))) stagingStore.setDates(projectId, task.id, start, due)
          else show(barOf(event), state.bar, task.startDate, task.dueDate)
        }
      },
      onPointerCancel --> { event =>
        drag.now().foreach(state => show(barOf(event), state.bar, task.startDate, task.dueDate))
        drag.set(None)
      }
    )
  }

  private def barElement(
    task: FindTaskRespFull,
    placed: GanttBar.Placed,
    axis: GanttAxis,
    taskLookups: BulkLookups
  ): HtmlElement = {
    val color = GanttAssigneeColors.classFor(task.assignedTo.map(_.id), taskLookups.members.map(_.id))
    val rounding = (placed.clippedLeft, placed.clippedRight) match {
      case (false, false) => "rounded-md"
      case (true, false)  => "rounded-r-md"
      case (false, true)  => "rounded-l-md"
      case (true, true)   => ""
    }
    div(
      cls(s"relative z-10 mx-1 my-2 flex h-9 items-center justify-between overflow-hidden $color px-1 text-xs text-white $rounding"),
      title := barTitle(task, task.startDate, task.dueDate),
      // Moving needs both real dates in sight, so a clipped bar can only be resized from its other end.
      if (placed.clippedLeft || placed.clippedRight) emptyMod
      else Seq[Modifier[HtmlElement]](cls("cursor-grab select-none")) ++ dragModifiers(task, placed, axis, GanttEdge.Body),
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

  /** Laid out as in the bulk add: the name with the labels right after it, and the other badges in a row below. The
    * badges edit the task like everywhere else.
    */
  private def taskDetails(task: FindTaskRespFull): HtmlElement = {
    val badges = taskBadges(task)
    div(
      cls("flex min-w-0 flex-col gap-1 py-1"),
      div(
        cls("flex items-center gap-1.5"),
        span(cls("min-w-0 truncate text-sm text-heading"), title := task.name, task.name),
        badges.labels.amend(cls("shrink-0"))
      ),
      badges.primary
    )
  }

  private def setDraggable(element: dom.EventTarget, draggable: Boolean): Unit =
    element.asInstanceOf[dom.html.Element].setAttribute("draggable", draggable.toString)

  private def taskRow(task: FindTaskRespFull, axis: GanttAxis, taskLookups: BulkLookups): HtmlElement =
    div(
      cls("relative grid items-center border-b border-default-medium"),
      styleAttr := gridTemplate(axis),
      rowReorder.dragModifiers(task.id),
      // The row can be dragged to reorder only by the name cell: it becomes draggable when that is pressed, and stops
      // being so when the press ends. A press on a bar or on the empty days then never starts dragging the row.
      draggable(false),
      onDragEnd --> (event => setDraggable(event.currentTarget, draggable = false)),
      onMouseUp --> (event => setDraggable(event.currentTarget, draggable = false)),
      div(
        cls("flex cursor-pointer items-center gap-1 self-stretch overflow-hidden px-1 py-1"),
        onPointerDown --> (event => setDraggable(event.currentTarget.asInstanceOf[dom.html.Element].parentElement, draggable = true)),
        taskDetails(task)
      ),
      // The day cells sit behind the bar so the columns show through.
      axis.days.indices.map(index =>
        div(cls(s"h-full ${dayCellClasses(axis, index)}"), styleAttr := s"grid-column: ${index + 2}; grid-row: 1")
      ),
      GanttBar.of(task.startDate, task.dueDate, axis).fold[Modifier[HtmlElement]](emptyMod) {
        case placed: GanttBar.Placed => barElement(task, placed, axis, taskLookups).amend(styleAttr := barStyle(placed))
        case GanttBar.Outside(side)  => outsideElement(side).amend(styleAttr := outsideStyle(axis))
      }
    )

  private def barStyle(placed: GanttBar.Placed): String =
    s"grid-column: ${placed.startCol + 2} / span ${placed.width}; grid-row: 1"

  private def outsideStyle(axis: GanttAxis): String = s"grid-column: 2 / span ${axis.size}; grid-row: 1"

  private def emptyMessage(text: String): HtmlElement = p(cls("p-3 text-sm text-body-subtle"), text)

  private def chart(axis: GanttAxis, tasks: Seq[FindTaskRespFull], taskLookups: BulkLookups): HtmlElement =
    if (axis.isEmpty) emptyMessage("Pick a valid start and end date with at least one work day to see the Gantt chart.")
    else if (tasks.isEmpty) emptyMessage("Stage some tasks to see them on the Gantt chart.")
    else
      div(
        cls("overflow-x-auto rounded-base border border-default-medium"),
        div(
          styleAttr := s"min-width: ${NameColumnRem + axis.size * 2.25}rem",
          headerRow(axis),
          tasks.map(taskRow(_, axis, taskLookups))
        )
      )

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Gantt chart", TgCard.CardHeaderSize.H5),
        body = TgCard.HtmlCardContent(
          div(child <-- axisSignal.combineWith(orderedTasks, lookups).map { case (axis, tasks, taskLookups) =>
            chart(axis, tasks, taskLookups)
          })
        )
      )
      .element
      .amend(lookups.map(_.members.map(_.id)) --> memberIds)
}
