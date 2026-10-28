package com.togger.logentry

import com.togger.chart.{ChartColor, Chartable}
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class LogEntryStatus(
  val value: Int,
  val chartColor: ChartColor,
  val emoji: String,
  val statusName: String,
  val description: String
) extends IntEnumEntry
    with Chartable {

  def key: String = s"log-entry-status.$this"

  def label: String = statusName

  def toStringWithEmoji: String = s"$emoji $statusName"

}

object LogEntryStatus extends IntEnum[LogEntryStatus] {

  case object InProgress extends LogEntryStatus(1, ChartColor.Blue, "🔵", "In progress", "Things are moving")
  case object Blocked extends LogEntryStatus(2, ChartColor.Red, "🔴", "Blocked", "This cannot proceed")
  case object Completed extends LogEntryStatus(3, ChartColor.Green, "🟢", "Completed", "This has been completed")
  case object Abandoned extends LogEntryStatus(4, ChartColor.Dark, "⚫️", "Abandoned", "This has been given up on")
  case object Paused extends LogEntryStatus(5, ChartColor.Yellow, "🟡️", "Paused", "This is on hold, to be resumed later")
  case object Holiday extends LogEntryStatus(6, ChartColor.Orange, "🎄", "Holiday", "Out on a public holiday")
  case object Vacation extends LogEntryStatus(7, ChartColor.Orange, "🏝", "Vacation", "Out on vacation")

  def namesToValuesMapString: Map[String, String] =
    this.values.map { status => status.value.toString -> status.toStringWithEmoji }.toMap

  override def values: IndexedSeq[LogEntryStatus] = findValues

}
