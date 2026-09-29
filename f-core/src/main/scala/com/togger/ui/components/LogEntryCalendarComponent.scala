package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.dto.LogEntryDto
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.task.TgDateFormater

import java.time.{LocalDate, ZoneOffset}

/** A Mon-Fri grid of a task's log entries, one cell per weekday, each showing that day's status emoji(s). Clicking a
  * cell toggles it in `selectedDayVar`, which a caller can read to drive a "selected day" detail view elsewhere.
  */
class LogEntryCalendarComponent(
  logEntries: Seq[LogEntryDto],
  tgDateFormater: TgDateFormater,
  selectedDayVar: Var[Option[LocalDate]] = Var(None)
) {
  import LogEntryCalendarComponent.isWeekend

  val element: HtmlDiv = {
    val weekdayEntries =
      logEntries.filterNot(entry => isWeekend(entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate))

    if (weekdayEntries.isEmpty) div()
    else {
      val entriesByDate: Map[LocalDate, Seq[LogEntryDto]] =
        weekdayEntries.groupBy(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)

      val minDate = entriesByDate.keys.min
      val maxDate = entriesByDate.keys.max

      val calendarStart = minDate.minusDays(minDate.getDayOfWeek.getValue - 1L)
      val calendarEnd = maxDate.plusDays(5L - maxDate.getDayOfWeek.getValue)

      val allDays =
        Iterator
          .iterate(calendarStart)(_.plusDays(1))
          .takeWhile(!_.isAfter(calendarEnd))
          .filterNot(isWeekend)
          .toSeq

      div(
        marginBottom.rem(1),
        cls("grid grid-cols-5 gap-1"),
        Seq("Mon", "Tue", "Wed", "Thu", "Fri")
          .map(label => div(cls("text-xs font-semibold text-center text-gray-500"), label)),
        allDays.map(day => calendarDayCell(day, entriesByDate.getOrElse(day, Seq.empty).sortBy(_.createdAt)))
      )
    }
  }

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

  def isWeekend(day: LocalDate): Boolean = {
    val dow = day.getDayOfWeek.getValue
    dow == 6 || dow == 7
  }

  def lastCalendarDay(logEntries: Seq[LogEntryDto]): Option[LocalDate] =
    logEntries
      .map(entry => entry.createdAt.atOffset(ZoneOffset.UTC).toLocalDate)
      .filterNot(isWeekend)
      .maxOption

}
