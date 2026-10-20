package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.dto.LogEntryDto
import com.togger.logentry.LogEntryStatus
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import com.togger.ui.task.TgDateFormater

import java.time.{DayOfWeek, LocalDate, ZoneOffset}

/** A grid of a task's log entries, one column per the project's configured work day, each showing that day's status
  * emoji(s). Clicking a cell toggles it in `selectedDayVar`, which a caller can read to drive a "selected day" detail
  * view elsewhere.
  *
  * `tinyElement` is the same grid in a mini shape for tight spots: one letter per column, the emoji and the day of the month (with the month where one starts) in small
  * cells, `TinyWeeks` weeks at a time, the latest first, with arrows to step back and forward. Clicking a cell toggles it in `selectedDayVar`, as in the big one. The `markedDay` gets a brand border and is always part of the grid, as are the `extraDays`. A status in `pendingStatuses` is shown faded on a dashed cell: it is not saved yet. The task's `startDate` and `dueDate` are marked
  * on it when they fall in the days shown.
  */
class LogEntryCalendarComponent(
  logEntries: Seq[LogEntryDto],
  tgDateFormater: TgDateFormater,
  workDays: Set[DayOfWeek],
  selectedDayVar: Var[Option[LocalDate]] = Var(None),
  startDate: Option[LocalDate] = None,
  dueDate: Option[LocalDate] = None,
  markedDay: Option[LocalDate] = None,
  extraDays: Seq[LocalDate] = Seq.empty,
  pendingStatuses: Signal[Map[LocalDate, LogEntryStatus]] = Val(Map.empty)
) {
  import LogEntryCalendarComponent.{DateMark, isWorkDay, TinyWeeks}

  private val sortedWorkDays: Seq[DayOfWeek] = workDays.toSeq.sortBy(_.getValue)

  val element: HtmlDiv = render(tiny = false)

  val tinyElement: HtmlDiv = render(tiny = true)

  private def render(tiny: Boolean): HtmlDiv = {
    val workDayEntries =
      logEntries.filter(entry => isWorkDay(entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate, workDays))

    if (workDayEntries.isEmpty || sortedWorkDays.isEmpty) div()
    else {
      val entriesByDate: Map[LocalDate, Seq[LogEntryDto]] =
        workDayEntries.groupBy(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)

      // the marked and the extra days are part of the grid even when they have no entry, as the days still to log have none
      val shownDates = entriesByDate.keys ++ markedDay ++ extraDays.filter(isWorkDay(_, workDays))
      val minDate = shownDates.min
      val maxDate = shownDates.max

      // Rounded out to the first/last work day of their respective weeks, so the grid always starts and ends on
      // a full week -- otherwise the first/last row's cells wouldn't line up under the right column headers.
      val calendarStart = Iterator.iterate(minDate)(_.minusDays(1)).find(_.getDayOfWeek == sortedWorkDays.head).get
      val calendarEnd = Iterator.iterate(maxDate)(_.plusDays(1)).find(_.getDayOfWeek == sortedWorkDays.last).get

      val allDays =
        Iterator
          .iterate(calendarStart)(_.plusDays(1))
          .takeWhile(!_.isAfter(calendarEnd))
          .filter(day => workDays.contains(day.getDayOfWeek))
          .toSeq
      val pageSize = TinyWeeks * sortedWorkDays.size
      val pageCount = (allDays.size + pageSize - 1) / pageSize
      // the mini calendar shows one page of weeks at a time, the latest first, and opens on the page of the marked day
      val markedPage = markedDay.map(allDays.indexOf).filter(_ >= 0).map(index => (allDays.size - 1 - index) / pageSize)
      val page = Var(markedPage.getOrElse(0))

      def cells(shownDays: Seq[LocalDate]) =
        shownDays.zipWithIndex.map { case (day, index) =>
          val entries = entriesByDate.getOrElse(day, Seq.empty).sortBy(_.createdAt)
          // the month is named on the first day shown and wherever a new month starts
          val startsMonth = index == 0 || shownDays(index - 1).getMonth != day.getMonth
          if (tiny) tinyDayCell(day, entries, startsMonth, dateMarks(day)) else calendarDayCell(day, entries)
        }

      def pageDays(current: Int): Seq[LocalDate] =
        allDays.slice(math.max(allDays.size - (current + 1) * pageSize, 0), allDays.size - current * pageSize)

      def pageButton(label: String, icon: SvgElement, delta: Int) =
        button(
          `type`("button"),
          cls("flex h-5 w-5 items-center justify-center rounded-sm text-body hover:bg-neutral-tertiary-medium cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"),
          aria.label(label),
          title(label),
          icon,
          disabled <-- page.signal.map(current => current + delta < 0 || current + delta >= pageCount),
          onClick.mapToUnit --> (_ => page.update(_ + delta))
        )

      val grid = div(
        if (tiny) emptyMod else marginBottom.rem(1),
        // Tailwind only generates CSS for class names it can find as literal text in the source, so a dynamic
        // column count (1..7, driven by the project's configured work days) can't be a `grid-cols-N` utility
        // class -- it has to be an inline style instead, same as MilestoneHistoryComponent's dynamic-width grid.
        styleAttr := (if (tiny)
                        s"display: grid; grid-template-columns: repeat(${sortedWorkDays.size}, 2.5rem); gap: 0.125rem;"
                      else
                        s"display: grid; grid-template-columns: repeat(${sortedWorkDays.size}, minmax(0, 1fr)); gap: 0.25rem;"),
        sortedWorkDays.map { day =>
          if (tiny) div(cls("text-xs leading-3 font-semibold text-center text-body-subtle"), dayAbbrev(day).take(1))
          else div(cls("text-xs font-semibold text-center text-body-subtle"), dayAbbrev(day))
        },
        if (tiny) children <-- page.signal.map(current => cells(pageDays(current))) else cells(allDays)
      )

      if (tiny)
        div(
          cls("w-fit"),
          div(
            cls("mb-1 flex items-center justify-between"),
            pageButton(s"Back $TinyWeeks weeks", TgIcons.chevronLeftIcon("w-3 h-3"), delta = 1),
            button(
              `type`("button"),
              cls("rounded-sm px-1.5 text-[11px] font-medium leading-5 text-body hover:bg-neutral-tertiary-medium cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"),
              title("Latest weeks"),
              "Today",
              disabled <-- page.signal.map(_ == 0),
              onClick.mapToUnit --> (_ => page.set(0))
            ),
            pageButton(s"Forward $TinyWeeks weeks", TgIcons.chevronRightIcon("w-3 h-3"), delta = -1)
          ),
          grid
        )
      else grid
    }
  }

  private def monthAbbrev(day: LocalDate): String = day.getMonth.name().toLowerCase.capitalize.take(3)

  private def dayAbbrev(day: DayOfWeek): String = day.name().toLowerCase.capitalize.take(3)

  private def tooltipOf(day: LocalDate, entries: Seq[LogEntryDto]): String =
    if (entries.isEmpty) ""
    else (tgDateFormater.asAmericanDate(day) +: entries.map(_.status.toStringWithEmoji)).mkString("\n")

  private def dateMarks(day: LocalDate): Seq[DateMark] =
    Seq(
      Option.when(startDate.contains(day))(DateMark("S", "Start date", "bg-green-600")),
      Option.when(dueDate.contains(day))(DateMark("D", "Due date", "bg-red-600"))
    ).flatten

  private def tinyDayCell(day: LocalDate, entries: Seq[LogEntryDto], startsMonth: Boolean, marks: Seq[DateMark]) =
    div(
      cls("relative flex h-10 w-10 cursor-pointer items-center justify-center overflow-hidden rounded-sm border border-default-medium text-sm leading-none"),
      cls(if (entries.isEmpty) "bg-neutral-primary" else "bg-neutral-secondary-medium"),
      cls <-- selectedDayVar.signal.map(selected => if (selected.contains(day)) "ring-2 ring-blue-400" else ""),
      cls(if (markedDay.contains(day)) "border-brand!" else ""),
      cls <-- pendingStatuses.map(pending => if (pending.contains(day)) "border-dashed" else ""),
      title((tooltipOf(day, entries) +: marks.map(_.title)).filter(_.nonEmpty).mkString("\n")),
      onClick.mapToUnit --> (_ => selectedDayVar.update(current => if (current.contains(day)) None else Some(day))),
      span(cls("absolute start-1 top-1 text-[10px] leading-none text-body-subtle"), if (startsMonth) s"${day.getDayOfMonth} ${monthAbbrev(day)}" else day.getDayOfMonth.toString),
      entries.lastOption.map(entry => span(cls("mt-2"), entry.status.emoji)),
      child.maybe <-- pendingStatuses.map(_.get(day).map { status =>
        // beside a saved entry it goes in the corner, so both can be told apart
        val placement = if (entries.isEmpty) "mt-2" else "absolute bottom-0.5 start-0.5 text-[10px]"
        span(cls(placement, "opacity-50"), title("Will be added"), status.emoji)
      }),
      div(
        cls("absolute bottom-0.5 end-0.5 flex gap-px"),
        marks.map(mark =>
          span(cls("rounded-sm px-0.5 text-[8px] font-bold leading-3 text-white"), cls(mark.colorCls), mark.letter)
        )
      )
    )

  private def calendarDayCell(day: LocalDate, entries: Seq[LogEntryDto]) = {
    val tooltip = tooltipOf(day, entries)

    div(
      cls("border border-default-medium rounded-md cursor-pointer"),
      cls(if (entries.isEmpty) "bg-neutral-primary" else "bg-neutral-secondary-medium"),
      cls("p-1"),
      cls <-- selectedDayVar.signal.map(selected => if (selected.contains(day)) "ring-2 ring-blue-400" else ""),
      textAlign.center,
      title(tooltip),
      onClick.mapToUnit --> (_ => selectedDayVar.update(current => if (current.contains(day)) None else Some(day))),
      div(cls("text-xs text-body-subtle"), tgDateFormater.dayAndMonth(day)),
      div(cls("flex flex-wrap justify-center gap-0.5"), entries.map(entry => span(entry.status.emoji)))
    )
  }

}

object LogEntryCalendarComponent {

  /** A small letter on a day of the tiny calendar, such as the start or the due date of the task. */
  private case class DateMark(letter: String, title: String, colorCls: String)

  /** How many weeks the tiny calendar shows. */
  val TinyWeeks = 3

  def isWorkDay(day: LocalDate, workDays: Set[DayOfWeek]): Boolean = workDays.contains(day.getDayOfWeek)

  def lastCalendarDay(logEntries: Seq[LogEntryDto], workDays: Set[DayOfWeek]): Option[LocalDate] =
    logEntries
      .map(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)
      .filter(isWorkDay(_, workDays))
      .maxOption

}
