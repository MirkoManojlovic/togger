package com.togger.ui.timebox.gantt

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{FindTaskRespFull, LogEntryDto}
import com.togger.logentry.LogEntryStatus
import com.togger.markdown.TgMarkdown
import com.togger.ui.auth.TgCard
import com.togger.ui.logentry.LogEntryReadOnlyCard
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{LogEntryStatusSquare, TgBadge, TgTab, TgTabs}
import com.togger.ui.task.TgDateFormater

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The tasks of a timebox as a read only Gantt chart. Under the bar of each task, the planned duration, there is a
  * square per day with the status of the log entry of that day, to compare what was planned with what happened.
  */
class TimeboxReviewGanttComponent(
  tasks: Signal[Seq[FindTaskRespFull]],
  workDays: Signal[Set[DayOfWeek]],
  timebox: Signal[Option[(LocalDate, LocalDate)]],
  memberIds: Signal[Seq[UUID]],
  tgDateFormater: TgDateFormater,
  tgMarkdown: TgMarkdown,
  tgClock: TgClock
) {

  private val layout = new GanttChartLayout(tgDateFormater, tgClock)

  private val axisSignal: Signal[GanttAxis] =
    timebox
      .combineWith(workDays)
      .map { case (range, days) => range.fold(GanttAxis.empty) { case (start, end) => GanttAxis.of(start, end, days) } }
      .distinct

  private val entryByTaskAndDay: Signal[Map[(UUID, LocalDate), LogEntryDto]] =
    tasks.map(all => GanttLogStatus.latestByTaskAndDay(all.flatMap(_.logEntries)))

  /** The task and day whose log entry is shown under the chart. */
  private val selectedVar = Var(Option.empty[(UUID, LocalDate)])

  private val PickTaskText = "Click a status square to see the log entries of its task"

  private val tabVar = Var("single-day")

  private def toggle(key: (UUID, LocalDate)): Unit =
    selectedVar.update(current => if (current.contains(key)) None else Some(key))
    tabVar.set("single-day")

  private def taskDetails(task: FindTaskRespFull): HtmlElement =
    div(
      cls("flex min-w-0 flex-col items-start gap-1 py-1"),
      span(cls("min-w-0 max-w-full truncate text-sm text-heading"), title := task.name, task.name),
      TgBadge(text = task.taskStatus.name, bordered = true, color = task.taskStatus.badgeColor).element
    )

  private def statusCells(
    task: FindTaskRespFull,
    axis: GanttAxis,
    entries: Map[(UUID, LocalDate), LogEntryDto]
  ): Seq[HtmlElement] =
    axis.days.zipWithIndex.map { case (day, index) =>
      val key = (task.id, day)
      val entry = entries.get(key)
      val square = LogEntryStatusSquare(entry.map(_.status))
      div(
        cls(s"flex h-full items-center justify-center py-1 ${layout.dayCellClasses(axis, index)}"),
        styleAttr := s"grid-column: ${index + 2}; grid-row: 2",
        entry match {
          case Some(_) =>
            Seq[Modifier[HtmlElement]](
              cls("cursor-pointer"),
              onClick.mapToUnit --> (_ => toggle(key)),
              square.amend(cls("ring-2 ring-blue-400") <-- selectedVar.signal.map(_.contains(key)))
            )
          case None => square
        }
      )
    }

  private def taskRow(
    task: FindTaskRespFull,
    axis: GanttAxis,
    members: Seq[UUID],
    entries: Map[(UUID, LocalDate), LogEntryDto]
  ): HtmlElement =
    div(
      cls("relative grid items-center border-b border-default-medium"),
      styleAttr := layout.gridTemplate(axis),
      div(
        cls("flex items-center self-stretch overflow-hidden px-2 py-1"),
        styleAttr := "grid-column: 1; grid-row: 1 / span 2",
        taskDetails(task)
      ),
      layout.dayCells(axis, "1"),
      layout.barOrOutside(task, axis, (t, placed) => layout.barElement(t, placed, members)),
      statusCells(task, axis, entries)
    )

  private def legend(statuses: Seq[LogEntryStatus]): HtmlElement =
    div(
      cls("mb-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-body-subtle"),
      span(cls("font-medium"), "Log entry of the day:"),
      statuses.sortBy(_.value).map(status =>
        span(cls("flex items-center gap-1.5"), LogEntryStatusSquare(Some(status)), status.statusName)
      ),
      span(cls("flex items-center gap-1.5"), LogEntryStatusSquare(None), "No log entry")
    )

  private def chart(
    axis: GanttAxis,
    all: Seq[FindTaskRespFull],
    members: Seq[UUID],
    entries: Map[(UUID, LocalDate), LogEntryDto]
  ): HtmlElement =
    if (axis.isEmpty) layout.emptyMessage("The timebox has no work days to show on the Gantt chart.")
    else if (all.isEmpty) layout.emptyMessage("The timebox has no tasks.")
    else {
      val shown = all.flatMap(_.logEntries).map(_.status).distinct
      div(
        legend(shown),
        layout.frame(axis, GanttRowOrder.ordered(all, Seq.empty, members).map(taskRow(_, axis, members, entries)))
      )
    }

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Planned vs. actual", TgCard.CardHeaderSize.H4),
        body = TgCard.HtmlCardContent(
          div(
            child <-- axisSignal
              .combineWith(tasks, memberIds, entryByTaskAndDay)
              .map { case (axis, all, members, entries) => chart(axis, all, members, entries) }
          )
        )
      )
      .element
      .amend(cls("w-full mb-4"))

  private def entriesOf(all: Seq[FindTaskRespFull], inRange: LocalDate => Boolean): Seq[(FindTaskRespFull, LogEntryDto)] =
    all
      .flatMap(task => task.logEntries.map(task -> _))
      .filter { case (_, entry) => inRange(GanttLogStatus.toLocalDate(entry.createdAt)) }
      .sortBy { case (_, entry) => entry.createdAt }

  private def entryCards(found: Seq[(FindTaskRespFull, LogEntryDto)], emptyText: String): HtmlElement =
    div(
      cls("mb-4"),
      if (found.isEmpty) p(emptyText)
      else
        found.map { case (task, entry) =>
          LogEntryReadOnlyCard(entry, GanttLogStatus.toLocalDate(entry.createdAt), task.name, tgDateFormater, tgMarkdown)
            .amend(cls("mb-2"))
        }
    )

  private def rangeTab(id: String, label: String, inRange: LocalDate => Boolean): TgTab =
    TgTab(
      id = id,
      label = label,
      content = div(
        child <-- selectedVar.signal.combineWith(tasks).map {
          case (None, _)                  => p(PickTaskText)
          case (Some((taskId, _)), all) => entryCards(entriesOf(all.filter(_.id == taskId), inRange), "No status update")
        }
      )
    )

  /** The log entries of the clicked square, as they look on the task page. */
  private val singleDayTab: TgTab =
    TgTab(
      id = "single-day",
      label = "Single day",
      content = div(
        child <-- selectedVar.signal.combineWith(tasks).map {
          case (None, _) => p(PickTaskText)
          case (Some((taskId, day)), all) =>
            entryCards(
              entriesOf(all.filter(_.id == taskId), _ == day),
              "No status update"
            )
        }
      )
    )

  /** As on the project status page, the week is the current one, Monday to Sunday. */
  private def tabs: Seq[TgTab] = {
    val today = tgClock.currentDate()
    val weekStart = today.minusDays(today.getDayOfWeek.getValue - 1L)
    val weekEnd = weekStart.plusDays(6L)
    Seq(
      singleDayTab,
      rangeTab("last-week", "Last week", day => !day.isBefore(weekStart) && !day.isAfter(weekEnd)),
      rangeTab("last-2-weeks", "Last 2 weeks", day => !day.isBefore(weekStart.minusDays(7L)) && !day.isAfter(weekEnd)),
      rangeTab("everything", "Everything", _ => true)
    )
  }

  /** The log entries under the chart: of the clicked day, or of the last weeks or all of them, of the clicked task. */
  val detailElement: HtmlElement =
    div(cls("w-full mb-4"), new TgTabs(tabs, tabVar).element)
}
