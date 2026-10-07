package com.togger.timebox

import java.time.temporal.TemporalAdjusters
import java.time.{DayOfWeek, LocalDate}

case class TimeboxDefault(label: String, startDate: LocalDate, endDate: LocalDate)

object TimeboxDefaults {

  private val presetWeeks: List[(String, Int)] =
    List("One week" -> 1, "Two weeks" -> 2, "One month" -> 4, "Two months" -> 8, "Three months" -> 12)

  def nextMonday(today: LocalDate): LocalDate =
    today.`with`(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

  /** Every default starts on the next Monday (today when it is Monday) and ends on a Sunday. */
  def defaults(today: LocalDate): List[TimeboxDefault] = {
    val start = nextMonday(today)
    presetWeeks.map { case (label, weeks) => TimeboxDefault(label, start, start.plusWeeks(weeks.toLong).minusDays(1)) }
  }

}
