package com.togger.ui.milestone

import com.togger.common.WorkDayCalendar
import com.togger.dto.FindTaskRespFull
import com.togger.ui.components.TgBadgeColor
import com.togger.ui.timebox.TimeboxReviewStats
import enumeratum.{Enum, EnumEntry}

import java.time.{DayOfWeek, LocalDate}

/** How the work still to do compares with the work days left until the due date of a milestone. */
sealed abstract class MilestoneEstimationStatus(val label: String, val color: TgBadgeColor) extends EnumEntry

object MilestoneEstimationStatus extends Enum[MilestoneEstimationStatus] {

  override def values: IndexedSeq[MilestoneEstimationStatus] = findValues

  case object NotGoingToMakeIt extends MilestoneEstimationStatus("Not going to make it", TgBadgeColor.Red)
  case object ProbablyNotGoingToMakeIt
      extends MilestoneEstimationStatus("Probably not going to make it", TgBadgeColor.Orange)
  case object TightFinish extends MilestoneEstimationStatus("Tight Finish", TgBadgeColor.Yellow)
  case object ComfortableFinish extends MilestoneEstimationStatus("Comfortable Finish", TgBadgeColor.Lime)
  case object OnTrack extends MilestoneEstimationStatus("On Track", TgBadgeColor.Green)
}

/** The estimate of a milestone: the buffer is the work days until the due date minus the work days of work left. */
object MilestoneEstimation {

  val ComfortableBuffer = 10
  val OnTrackBuffer = 20

  def statusOf(buffer: Int): MilestoneEstimationStatus =
    if (buffer < 0) MilestoneEstimationStatus.NotGoingToMakeIt
    else if (buffer == 0) MilestoneEstimationStatus.ProbablyNotGoingToMakeIt
    else if (buffer < ComfortableBuffer) MilestoneEstimationStatus.TightFinish
    else if (buffer < OnTrackBuffer) MilestoneEstimationStatus.ComfortableFinish
    else MilestoneEstimationStatus.OnTrack

  /** A milestone without tasks left to do is on track whatever the date. */
  def estimate(
    tasks: Seq[FindTaskRespFull],
    today: LocalDate,
    dueDate: LocalDate,
    workDays: Set[DayOfWeek]
  ): MilestoneEstimationStatus = {
    if (tasks.forall(TimeboxReviewStats.isDone)) return MilestoneEstimationStatus.OnTrack
    val work = TimeboxReviewStats.workDaysProgress(tasks, today, workDays)
    val untilDue = WorkDayCalendar.countWorkDays(today, dueDate, workDays)
    statusOf(untilDue - work.left)
  }
}
