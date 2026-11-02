package com.togger.ui.task

import com.togger.ui.task.TgDateFormat.AmericanMonthName
import com.togger.ui.task.TgDateFormat.DayOnlyFormat
import com.togger.ui.task.TgDateFormat.LogEntryFormat
import com.togger.ui.task.TgDateFormat.MonthNameFormat
import com.togger.ui.task.TgDateFormat.YearMonthDayTimeFormat
import enumeratum.Enum
import enumeratum.EnumEntry

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TgDateFormater {
  def asAmericanDate(date: LocalDate): String =
    AmericanMonthName.formater.format(date)

  def asAmericanDateWithDayAbrv(date: LocalDate): String =
    s"${AmericanMonthName.formater.format(date)} | ${dayOfWeekAbbrev(date)}"

  def asStartDate(date: LocalDate): String = s"${DateEmoji.Start} ${asAmericanDateWithDayAbrv(date)}"

  def asDueDate(date: LocalDate): String = s"${DateEmoji.Due} ${asAmericanDateWithDayAbrv(date)}"

  def asEndDate(date: LocalDate): String = s"${DateEmoji.End} ${asAmericanDateWithDayAbrv(date)}"

  def dayAndMonth(date: LocalDate): String =
    LogEntryFormat.formater.format(date)

  def dayOfWeekAbbrev(date: LocalDate): String =
    date.getDayOfWeek.name().toLowerCase.capitalize.take(3)

  def dayOnly(date: LocalDate): String =
    DayOnlyFormat.formater.format(date)

  def monthName(date: LocalDate): String =
    MonthNameFormat.formater.format(date)

  def yearMonthDayTimeFormat(instant: Instant): String =
    YearMonthDayTimeFormat.formater.format(
      instant.atOffset(ZoneOffset.UTC)
    ) // When converting from instant an error is thrown
}

/** The emoji that marks a start, due and end date in the date badges. */
object DateEmoji {
  val Start = "🗓️"
  val Due = "🎯"
  val End = "✅"
}

sealed abstract class TgDateFormat(val formater: DateTimeFormatter) extends EnumEntry

object TgDateFormat extends Enum[TgDateFormat] {

  override def values: IndexedSeq[TgDateFormat] = findValues

  case object AmericanMonthName extends TgDateFormat(DateTimeFormatter.ofPattern("MMM d, yyyy"))

//  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd-MMM-Y"))
  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd MMM"))

  case object YearMonthDayTimeFormat extends TgDateFormat(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

  case object DayOnlyFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd"))

  case object MonthNameFormat extends TgDateFormat(DateTimeFormatter.ofPattern("MMMM"))

}
