package com.togger.milestone

import com.togger.chart.{ChartColor, Chartable}
import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class MilestoneStepStatus(
  val value: Int,
  val statusName: String,
  val description: String,
  val emoji: String,
  val chartColor: ChartColor
) extends IntEnumEntry
    with Chartable {

  def key: String = s"milestone-step-status.$this"

  def label: String = statusName

  def toStringWithEmoji: String = s"$emoji $statusName"

  def isClosing: Boolean = MilestoneStepStatus.closing.contains(this)

}

object MilestoneStepStatus extends IntEnum[MilestoneStepStatus] {

  case object Planned extends MilestoneStepStatus(0, "Planned", "Not started yet", "🗓️", ChartColor.Gray)
  case object InProgress extends MilestoneStepStatus(1, "In Progress", "Things are moving", "▶️", ChartColor.Blue)
  case object Done extends MilestoneStepStatus(2, "Done", "This has been completed", "✅", ChartColor.Green)
  case object Paused extends MilestoneStepStatus(3, "Paused", "This is on hold for now", "⏸️", ChartColor.Yellow)
  case object Abandoned extends MilestoneStepStatus(4, "Abandoned", "This will not be continued", "🗑️", ChartColor.Dark)

  override def values: IndexedSeq[MilestoneStepStatus] = findValues

  /** Statuses a step can be closed with; they need an end date. */
  val closing: IndexedSeq[MilestoneStepStatus] = IndexedSeq(Done, Abandoned)

}
