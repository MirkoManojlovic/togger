package com.togger.ui.components

import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDate}
import java.util.Locale
import scala.util.Try

object DatePickerInput {

  private val dayOnly = "^\\d{1,2}$".r

  /** Parses either the `yyyy-MM-dd` shape `TgDateFormater.asAmericanDate` produces, or a bare 1-2 digit day (e.g.
    * "12"), which is taken as a day in `today`'s month/year. Anything else parses to `None` rather than throwing.
    */
  def parse(raw: String, today: LocalDate): Option[LocalDate] = {
    val trimmed = raw.trim
    trimmed match {
      case dayOnly() => Try(today.withDayOfMonth(trimmed.toInt)).toOption
      case _         => Try(LocalDate.parse(trimmed)).toOption
    }
  }
}

/** One quick-pick row in a [[CalendarPickerPopup]]'s suggestion dropdown: `resolve` turns "today" into the suggested
  * date, e.g. `today => today.plusDays(1)` for "Tomorrow".
  */
case class DateSuggestion(label: String, resolve: LocalDate => LocalDate)

object DatePickerSuggestions {

  /** Weekdays only, Monday-Friday — this app has no use for picking a weekend date via a quick-pick. */
  private val weekdays: Seq[DayOfWeek] =
    Seq(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  private def name(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

  val all: Seq[DateSuggestion] =
    Seq(
      DateSuggestion("Today", identity),
      DateSuggestion("Tomorrow", _.plusDays(1)),
      DateSuggestion("Yesterday", _.minusDays(1))
    ) ++
      weekdays.map(day => DateSuggestion(name(day), _.`with`(TemporalAdjusters.nextOrSame(day)))) ++
      weekdays.map(day => DateSuggestion(s"Previous ${name(day)}", _.`with`(TemporalAdjusters.previous(day)))) ++
      weekdays.map(day => DateSuggestion(s"Next ${name(day)}", _.`with`(TemporalAdjusters.next(day))))
}
