package com.togger.ui.components

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDate}
import scala.util.Try

object DatePickerInput {

  /** Parses the same `yyyy-MM-dd` shape `TgDateFormater.asAmericanDate` produces; anything else parses to `None` rather
    * than throwing.
    */
  def parse(raw: String): Option[LocalDate] = Try(LocalDate.parse(raw.trim)).toOption
}

/** One quick-pick row in a [[CalendarPickerPopup]]'s suggestion dropdown: `resolve` turns "today" into the suggested
  * date, e.g. `today => today.plusDays(1)` for "Tomorrow".
  */
case class DateSuggestion(label: String, resolve: LocalDate => LocalDate)

object DatePickerSuggestions {
  val all: Seq[DateSuggestion] = Seq(
    DateSuggestion("Today", identity),
    DateSuggestion("Tomorrow", _.plusDays(1)),
    DateSuggestion("Yesterday", _.minusDays(1)),
    DateSuggestion("Next Monday", _.`with`(TemporalAdjusters.next(DayOfWeek.MONDAY))),
    DateSuggestion("Next Friday", _.`with`(TemporalAdjusters.next(DayOfWeek.FRIDAY)))
  )
}
