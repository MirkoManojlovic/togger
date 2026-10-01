package com.togger.logentry

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class LogEntryStatus(
  val value: Int,
  val color: String,
  val emoji: String,
  val statusName: String,
  val description: String
) extends IntEnumEntry {

  def toStringWithEmoji: String = s"$emoji $statusName"

}

object LogEntryStatus extends IntEnum[LogEntryStatus] {

  case object InProgress extends LogEntryStatus(1, "#3B82F6", "🔵", "In progress", "Things are moving")
  case object Blocked extends LogEntryStatus(2, "#EF4444", "🔴", "Blocked", "This cannot proceed")
  case object Completed extends LogEntryStatus(3, "#22C55E", "🟢", "Completed", "This has been completed")
  case object Abandoned extends LogEntryStatus(4, "#000000", "⚫️", "Abandoned", "This has been given up on")
  case object Paused extends LogEntryStatus(5, "#EAB308", "🟡️", "Paused", "This is on hold, to be resumed later")
  case object Holiday extends LogEntryStatus(6, "#F97316", "🎄", "Holiday", "Out on a public holiday")
  case object Vacation extends LogEntryStatus(7, "#F97316", "🏝", "Vacation", "Out on vacation")
  case object Handover extends LogEntryStatus(8, "#A855F7", "🤲", "Handover", "This has been handed over to someone else")

  def namesToValuesMapString: Map[String, String] =
    this.values.map { status => status.value.toString -> status.toStringWithEmoji }.toMap

  override def values: IndexedSeq[LogEntryStatus] = findValues

}
