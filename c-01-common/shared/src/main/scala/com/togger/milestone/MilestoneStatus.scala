package com.togger.milestone

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class MilestoneStatus(val value: Int, val statusName: String, val color: String) extends IntEnumEntry

object MilestoneStatus extends IntEnum[MilestoneStatus] {

  case object InProgress extends MilestoneStatus(0, "In Progress", "#3F83F8")
  case object Done extends MilestoneStatus(1, "Done", "#057A55")
  case object Paused extends MilestoneStatus(2, "Paused", "#FACA15")
  case object Abandoned extends MilestoneStatus(3, "Abandoned", "#1F2937")

  override def values: IndexedSeq[MilestoneStatus] = findValues

}
