package com.togger.ui.components

import com.togger.logentry.LogEntryStatus

/** [[LogEntryStatus.color]] is a raw hex string, but [[TgBadge]] only accepts the fixed [[TgBadgeColor]] palette (so
  * it keeps working in dark mode). The hexes happen to line up with Tailwind's 500-shade swatches that
  * [[TgBadgeColor]]'s palette colors already use, so each one maps onto its matching [[TgBadgeColor]]; an unknown hex
  * falls back to [[TgBadgeColor.Gray]].
  */
object LogEntryStatusBadgeColor {

  private val hexToColor: Map[String, TgBadgeColor] = Map(
    "#3B82F6" -> TgBadgeColor.Blue,
    "#EF4444" -> TgBadgeColor.Red,
    "#22C55E" -> TgBadgeColor.Green,
    "#EAB308" -> TgBadgeColor.Yellow,
    "#F97316" -> TgBadgeColor.Orange,
    "#A855F7" -> TgBadgeColor.Purple,
    "#000000" -> TgBadgeColor.Dark
  )

  def of(status: LogEntryStatus): TgBadgeColor = hexToColor.getOrElse(status.color.toUpperCase, TgBadgeColor.Gray)

  /** Lets callers write `status.badgeColor` instead of `LogEntryStatusBadgeColor.of(status)`. */
  implicit class LogEntryStatusBadgeColorOps(private val status: LogEntryStatus) extends AnyVal {
    def badgeColor: TgBadgeColor = of(status)
  }

}
