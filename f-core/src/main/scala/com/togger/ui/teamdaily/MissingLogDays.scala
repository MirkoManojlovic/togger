package com.togger.ui.teamdaily

import java.time.LocalDate

/** The days the team daily asks about for a task: only today, whether or not it already has an entry (which is then
  * shown to be changed).
  */
object MissingLogDays {

  def of(today: LocalDate): Seq[LocalDate] = Seq(today)
}
