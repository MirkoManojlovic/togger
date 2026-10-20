package com.togger.ui.slacker

import java.time.LocalDate

/** The way a task's missing days are walked: from today into the past, or from the past up to today. */
sealed trait SlackerDirection {
  def order(days: Seq[LocalDate]): Seq[LocalDate]
}

object SlackerDirection {

  case object TodayToPast extends SlackerDirection {
    def order(days: Seq[LocalDate]): Seq[LocalDate] = days.sortBy(_.toEpochDay)(Ordering[Long].reverse)
  }

  case object PastToToday extends SlackerDirection {
    def order(days: Seq[LocalDate]): Seq[LocalDate] = days.sortBy(_.toEpochDay)
  }

  val Default: SlackerDirection = TodayToPast

  def of(pastFirst: Boolean): SlackerDirection = if (pastFirst) PastToToday else TodayToPast
}
