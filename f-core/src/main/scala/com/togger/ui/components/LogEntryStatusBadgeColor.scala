package com.togger.ui.components

import com.togger.logentry.LogEntryStatus

/** The [[TgBadgeColor]] of a log entry status, the same hue as it has in charts. */
object LogEntryStatusBadgeColor {

  def of(status: LogEntryStatus): TgBadgeColor = ChartColorBadgeColor.of(status)

  /** Lets callers write `status.badgeColor` instead of `LogEntryStatusBadgeColor.of(status)`. */
  implicit class LogEntryStatusBadgeColorOps(private val status: LogEntryStatus) extends AnyVal {
    def badgeColor: TgBadgeColor = of(status)
  }

}
