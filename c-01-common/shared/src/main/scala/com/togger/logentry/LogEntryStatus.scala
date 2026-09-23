package com.togger.logentry

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class LogEntryStatus(val value: Int, val color: String, val emoji: String, val statusName: String)
    extends IntEnumEntry {

  def toStringWithEmoji: String = s"$emoji $statusName"

}

object LogEntryStatus extends IntEnum[LogEntryStatus] {

  case object InProgress extends LogEntryStatus(1, "#3B82F6", "🔵", "In progress")
  case object Blocked extends LogEntryStatus(2, "#EF4444", "🔴", "Blocked")
  case object Completed extends LogEntryStatus(3, "#22C55E", "🟢", "Completed")
  case object Abandoned extends LogEntryStatus(4, "#000000", "⚫️", "Abandoned")
  case object Paused extends LogEntryStatus(5, "#EAB308", "🟡️", "Paused")
  case object Holiday extends LogEntryStatus(6, "#F97316", "🎄", "Holiday")
  case object Vacation extends LogEntryStatus(7, "#F97316", "🏝", "Vacation")
  case object Handover extends LogEntryStatus(8, "#A855F7", "🤲", "Handover")

  def namesToValuesMapString: Map[String, String] =
    this.values.map { status => status.value.toString -> status.toStringWithEmoji }.toMap

  override def values: IndexedSeq[LogEntryStatus] = findValues

}
