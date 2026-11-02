package com.togger.ui.task

import com.togger.ui.task.TgDateFormat.AmericanDashes
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
    AmericanDashes.formater.format(date)

  def asAmericanDateWithDayAbrv(date: LocalDate): String = {
    val day = dayOfWeekAbbrev(date)
    val americanDate = AmericanDashes.formater.format(date)
    s"$americanDate | $day"
  }

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

sealed abstract class TgDateFormat(val formater: DateTimeFormatter) extends EnumEntry

object TgDateFormat extends Enum[TgDateFormat] {

  override def values: IndexedSeq[TgDateFormat] = findValues

  case object AmericanDashes extends TgDateFormat(DateTimeFormatter.ofPattern("YYYY-MM-dd"))

//  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd-MMM-Y"))
  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd MMM"))

  case object YearMonthDayTimeFormat extends TgDateFormat(DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss"))

  case object DayOnlyFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd"))

  case object MonthNameFormat extends TgDateFormat(DateTimeFormatter.ofPattern("MMMM"))

}
