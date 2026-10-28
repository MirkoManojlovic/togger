package com.togger.ui.milestone

import java.time.{DayOfWeek, LocalDate}

sealed abstract class MilestoneSummaryPreset(val label: String)

object MilestoneSummaryPreset {
  case object CurrentWeek extends MilestoneSummaryPreset("Current week")
  case object PreviousWeek extends MilestoneSummaryPreset("Previous week")
  case object PreviousTwoWeeks extends MilestoneSummaryPreset("Previous 2 weeks")
  case object PreviousThreeWeeks extends MilestoneSummaryPreset("Previous 3 weeks")
  case object PreviousFourWeeks extends MilestoneSummaryPreset("Previous 4 weeks")
  case object CurrentMonth extends MilestoneSummaryPreset("Current month")
  case object LastMonth extends MilestoneSummaryPreset("Last month")
  case object LastTwoMonths extends MilestoneSummaryPreset("Last 2 months")
  case object LastThreeMonths extends MilestoneSummaryPreset("Last 3 months")
  case object LastSixMonths extends MilestoneSummaryPreset("Last 6 months")

  val weekRow: Seq[MilestoneSummaryPreset] =
    Seq(CurrentWeek, PreviousWeek, PreviousTwoWeeks, PreviousThreeWeeks, PreviousFourWeeks)

  val monthRow: Seq[MilestoneSummaryPreset] =
    Seq(CurrentMonth, LastMonth, LastTwoMonths, LastThreeMonths, LastSixMonths)

  val rows: Seq[Seq[MilestoneSummaryPreset]] = Seq(weekRow, monthRow)

  val all: Seq[MilestoneSummaryPreset] = rows.flatten
}

/** The date ranges the summary presets stand for, relative to today. */
object MilestoneSummaryPresets {

  import MilestoneSummaryPreset._

  private val DaysFromMondayToFriday = 4L

  def range(preset: MilestoneSummaryPreset, today: LocalDate): (LocalDate, LocalDate) =
    preset match {
      case CurrentWeek        => currentWeek(today)
      case PreviousWeek       => previousWeeks(1, today)
      case PreviousTwoWeeks   => previousWeeks(2, today)
      case PreviousThreeWeeks => previousWeeks(3, today)
      case PreviousFourWeeks  => previousWeeks(4, today)
      case LastMonth          => previousMonths(1, today)
      case LastTwoMonths      => previousMonths(2, today)
      case LastThreeMonths    => previousMonths(3, today)
      case LastSixMonths      => previousMonths(6, today)
      case CurrentMonth       => (today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth))
    }

  private def mondayOf(day: LocalDate): LocalDate =
    day.minusDays((day.getDayOfWeek.getValue - DayOfWeek.MONDAY.getValue).toLong)

  private def currentWeek(today: LocalDate): (LocalDate, LocalDate) = {
    val monday = mondayOf(today)
    (monday, monday.plusDays(DaysFromMondayToFriday))
  }

  /** From the Monday `weeks` weeks before the current one to the Friday of the week before the current one. */
  private def previousWeeks(weeks: Int, today: LocalDate): (LocalDate, LocalDate) = {
    val monday = mondayOf(today)
    (monday.minusWeeks(weeks.toLong), monday.minusWeeks(1).plusDays(DaysFromMondayToFriday))
  }

  /** From the first day of the month `months` months before the current one to the last day of the month before it. */
  private def previousMonths(months: Int, today: LocalDate): (LocalDate, LocalDate) = {
    val firstOfMonth = today.withDayOfMonth(1)
    (firstOfMonth.minusMonths(months.toLong), firstOfMonth.minusDays(1))
  }

}
