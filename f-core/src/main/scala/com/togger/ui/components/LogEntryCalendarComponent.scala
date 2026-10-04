package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.dto.LogEntryDto
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.task.TgDateFormater

import java.time.{DayOfWeek, LocalDate, ZoneOffset}

/** A grid of a task's log entries, one column per the project's configured work day, each showing that day's status
  * emoji(s). Clicking a cell toggles it in `selectedDayVar`, which a caller can read to drive a "selected day" detail
  * view elsewhere.
  */
class LogEntryCalendarComponent(
  logEntries: Seq[LogEntryDto],
  tgDateFormater: TgDateFormater,
  workDays: Set[DayOfWeek],
  selectedDayVar: Var[Option[LocalDate]] = Var(None)
) {
  import LogEntryCalendarComponent.isWorkDay

  private val sortedWorkDays: Seq[DayOfWeek] = workDays.toSeq.sortBy(_.getValue)

  val element: HtmlDiv = {
    val workDayEntries =
      logEntries.filter(entry => isWorkDay(entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate, workDays))

    if (workDayEntries.isEmpty || sortedWorkDays.isEmpty) div()
    else {
      val entriesByDate: Map[LocalDate, Seq[LogEntryDto]] =
        workDayEntries.groupBy(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)

      val minDate = entriesByDate.keys.min
      val maxDate = entriesByDate.keys.max

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

      div(
        marginBottom.rem(1),
        // Tailwind only generates CSS for class names it can find as literal text in the source, so a dynamic
        // column count (1..7, driven by the project's configured work days) can't be a `grid-cols-N` utility
        // class -- it has to be an inline style instead, same as MilestoneHistoryComponent's dynamic-width grid.
        styleAttr := s"display: grid; grid-template-columns: repeat(${sortedWorkDays.size}, minmax(0, 1fr)); gap: 0.25rem;",
        sortedWorkDays
          .map(day => div(cls("text-xs font-semibold text-center text-gray-500"), dayAbbrev(day))),
        allDays.map(day => calendarDayCell(day, entriesByDate.getOrElse(day, Seq.empty).sortBy(_.createdAt)))
      )
    }
  }

  private def dayAbbrev(day: DayOfWeek): String = day.name().toLowerCase.capitalize.take(3)

  private def calendarDayCell(day: LocalDate, entries: Seq[LogEntryDto]) = {
    val tooltip =
      if (entries.isEmpty) ""
      else (tgDateFormater.asAmericanDate(day) +: entries.map(_.status.toStringWithEmoji)).mkString("\n")

    div(
      cls("border-1 border-gray-200 rounded-md cursor-pointer"),
      cls(if (entries.isEmpty) "bg-white" else "bg-gray-50"),
      cls("p-1"),
      cls <-- selectedDayVar.signal.map(selected => if (selected.contains(day)) "ring-2 ring-blue-400" else ""),
      textAlign.center,
      title(tooltip),
      onClick.mapToUnit --> (_ => selectedDayVar.update(current => if (current.contains(day)) None else Some(day))),
      div(cls("text-xs text-gray-500"), tgDateFormater.dayAndMonth(day)),
      div(cls("flex flex-wrap justify-center gap-0.5"), entries.map(entry => span(entry.status.emoji)))
    )
  }

}

object LogEntryCalendarComponent {

  def isWorkDay(day: LocalDate, workDays: Set[DayOfWeek]): Boolean = workDays.contains(day.getDayOfWeek)

  def lastCalendarDay(logEntries: Seq[LogEntryDto], workDays: Set[DayOfWeek]): Option[LocalDate] =
    logEntries
      .map(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)
      .filter(isWorkDay(_, workDays))
      .maxOption

}
