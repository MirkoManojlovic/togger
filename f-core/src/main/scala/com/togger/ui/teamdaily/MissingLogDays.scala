package com.togger.ui.teamdaily

import java.time.LocalDate

/** The days a task still needs a log entry for in the team daily: only today, and only if it has no entry yet today. */
object MissingLogDays {

  def of(loggedDays: Iterable[LocalDate], today: LocalDate): Seq[LocalDate] =
    if (loggedDays.exists(_ == today)) Seq.empty else Seq(today)
}
