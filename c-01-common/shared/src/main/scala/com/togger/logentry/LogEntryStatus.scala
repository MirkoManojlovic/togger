package com.togger.logentry

import enumeratum.values.{IntEnum, IntEnumEntry}

//case class LogEntryStatusDb(
//  id: Long,
//  value: LogEntryStatus
//)

sealed abstract class LogEntryStatus(val value: Int, val color: String, val emoji: String) extends IntEnumEntry {

  def toStringWithEmoji: String = s"$emoji $toString"

}

object LogEntryStatus extends IntEnum[LogEntryStatus] {

  case object InProgress extends LogEntryStatus(1, "#3B82F6", "🔵") {
    override def toString: String = "In progress"
  }

  case object Blocked extends LogEntryStatus(2, "#EF4444", "🔴") {
    override def toString: String = "Blocked"
  }

  case object Completed extends LogEntryStatus(3, "#22C55E", "🟢") {
    override def toString: String = "Completed"
  }

  case object Abandoned extends LogEntryStatus(4, "#000000", "⚫️") {
    override def toString: String = "Abandoned"
  }

  case object Paused extends LogEntryStatus(5, "#EAB308", "🟡️") {
    override def toString: String = "Paused"
  }

  case object Holiday extends LogEntryStatus(6, "#F97316", "🎄") {
    override def toString: String = "Holiday"
  }

  case object Vacation extends LogEntryStatus(7, "#F97316", "🏝") {
    override def toString: String = "Vacation"
  }

  case object Handover extends LogEntryStatus(8, "#A855F7", "🤲") {
    override def toString: String = "Handover"
  }

  def namesToValuesMapString: Map[String, String] =
    this.values.map { status => status.value.toString -> status.toStringWithEmoji }.toMap

  override def values: IndexedSeq[LogEntryStatus] = findValues

}
