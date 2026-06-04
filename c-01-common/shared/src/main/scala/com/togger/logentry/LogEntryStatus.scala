package com.togger.logentry

import enumeratum.values.{IntEnum, IntEnumEntry}

//case class LogEntryStatusDb(
//  id: Long,
//  value: LogEntryStatus
//)

sealed abstract class LogEntryStatus(val value: Int, val color: String) extends IntEnumEntry

object LogEntryStatus extends IntEnum[LogEntryStatus] {

  case object InProgress extends LogEntryStatus(1, "🔵") {
    override def toString: String = "🔵 In progress"
  }

  case object Blocked extends LogEntryStatus(2, "🔴") {
    override def toString: String = "🔴 Blocked"
  }

  case object Completed extends LogEntryStatus(3, "🟢") {
    override def toString: String = "🟢 Completed"
  }

  case object Abandoned extends LogEntryStatus(4, "⚫️") {
    override def toString: String = "⚫️ Abandoned"
  }

  case object Paused extends LogEntryStatus(5, "🟡️") {
    override def toString: String = "🟡 Paused"
  }

  case object Holiday extends LogEntryStatus(6, "🎄") {
    override def toString: String = "🎄 Holiday"
  }

  case object Vacation extends LogEntryStatus(7, "🏝") {
    override def toString: String = "🏝️ Vacation"
  }

  case object Handover extends LogEntryStatus(8, "🤲") {
    override def toString: String = "🤲 Handover"
  }


  def namesToValuesMapString: Map[String, String] =
    this.values.map { status => status.value.toString -> status.toString }.toMap

  override def values: IndexedSeq[LogEntryStatus] = findValues

}
