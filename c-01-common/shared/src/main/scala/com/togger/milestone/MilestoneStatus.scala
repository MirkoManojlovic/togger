package com.togger.milestone

import com.togger.chart.{ChartColor, Chartable}
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class MilestoneStatus(val value: Int, val statusName: String, val chartColor: ChartColor)
    extends IntEnumEntry
    with Chartable {

  def key: String = s"milestone-status.$this"

  def label: String = statusName

}

object MilestoneStatus extends IntEnum[MilestoneStatus] {

  case object InProgress extends MilestoneStatus(0, "In Progress", ChartColor.Blue)
  case object Done extends MilestoneStatus(1, "Done", ChartColor.Green)
  case object Paused extends MilestoneStatus(2, "Paused", ChartColor.Yellow)
  case object Abandoned extends MilestoneStatus(3, "Abandoned", ChartColor.Dark)

  override def values: IndexedSeq[MilestoneStatus] = findValues

}
