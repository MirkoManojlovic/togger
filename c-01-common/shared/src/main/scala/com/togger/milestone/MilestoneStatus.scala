package com.togger.milestone

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class MilestoneStatus(val value: Int, val statusName: String) extends IntEnumEntry

object MilestoneStatus extends IntEnum[MilestoneStatus] {

  case object InProgress extends MilestoneStatus(0, "In Progress")
  case object Done extends MilestoneStatus(1, "Done")
  case object Paused extends MilestoneStatus(2, "Paused")
  case object Abandoned extends MilestoneStatus(3, "Abandoned")

  override def values: IndexedSeq[MilestoneStatus] = findValues

}
