package com.togger.ui.milestone

import java.time.{DayOfWeek, LocalDate}

sealed abstract class MilestoneSummaryPreset(val label: String)

object MilestoneSummaryPreset {
  case object OneWeek extends MilestoneSummaryPreset("1 week")
  case object TwoWeeks extends MilestoneSummaryPreset("2 weeks")
  case object OneMonth extends MilestoneSummaryPreset("1 month")

  val all: Seq[MilestoneSummaryPreset] = Seq(OneWeek, TwoWeeks, OneMonth)
}

/** The date ranges the summary presets stand for, relative to today. */
object MilestoneSummaryPresets {

  import MilestoneSummaryPreset._

  private val DaysFromMondayToFriday = 4L
  private val DaysFromMondayToNextFriday = 11L

  def range(preset: MilestoneSummaryPreset, today: LocalDate): (LocalDate, LocalDate) = {
    val monday = today.minusDays((today.getDayOfWeek.getValue - DayOfWeek.MONDAY.getValue).toLong)
    preset match {
      case OneWeek   => (monday, monday.plusDays(DaysFromMondayToFriday))
      case TwoWeeks  => (monday, monday.plusDays(DaysFromMondayToNextFriday))
      case OneMonth  => (today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth))
    }
  }

  /** The project has not configured the first or the last day of the range as a work day. */
  def misaligned(range: (LocalDate, LocalDate), workDays: Set[DayOfWeek]): Boolean =
    !workDays.contains(range._1.getDayOfWeek) || !workDays.contains(range._2.getDayOfWeek)

  def misalignedPresets(today: LocalDate, workDays: Set[DayOfWeek]): Seq[MilestoneSummaryPreset] =
    if (workDays.isEmpty) Seq.empty
    else MilestoneSummaryPreset.all.filter(preset => misaligned(range(preset, today), workDays))

}
