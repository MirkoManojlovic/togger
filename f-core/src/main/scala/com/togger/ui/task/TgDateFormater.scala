package com.togger.ui.task

import com.togger.ui.task.TgDateFormat.{EuropeanDots, LogEntryFormat}
import enumeratum.{Enum, EnumEntry}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TgDateFormater {
  def asEuropeanDate(date: LocalDate): String =
    EuropeanDots.formater.format(date)

  def asAmericanDate(date: LocalDate): (String, String) = {
    date.getDayOfWeek.name().toLowerCase.capitalize.take(3) -> LogEntryFormat.formater.format(date)
  }
}

sealed abstract class TgDateFormat(val formater: DateTimeFormatter) extends EnumEntry

object TgDateFormat extends Enum[TgDateFormat] {

  override def values: IndexedSeq[TgDateFormat] = findValues

  case object EuropeanDots extends TgDateFormat(DateTimeFormatter.ofPattern("dd.MM.YYYY"))

//  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd-MMM-Y"))
  case object LogEntryFormat extends TgDateFormat(DateTimeFormatter.ofPattern("dd MMM"))

}
