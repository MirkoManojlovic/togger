package com.togger.milestone

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class MilestoneStatus(val value: Int) extends IntEnumEntry

object MilestoneStatus extends IntEnum[MilestoneStatus] {

  case object Open extends MilestoneStatus(0)

  case object Closed extends MilestoneStatus(1)

  override def values: IndexedSeq[MilestoneStatus] = findValues

}
