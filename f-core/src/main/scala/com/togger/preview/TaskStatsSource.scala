package com.togger.preview

import java.time.LocalDate
import java.util.UUID

/** What the AI summary of a milestone reads from the tasks and their log entries of a period. */
case class MilestoneActivity(taskName: String, statusName: String, logTexts: List[String])

/** The window of the milestone/status/label domains into the tasks domain, which owns the tasks. */
trait TaskStatsSource {

  /** A task status that is used by a task cannot be deleted. */
  def statusInUse(statusId: UUID): Boolean

  /** The tasks of the milestone that have log entries within the period (both ends inclusive). */
  def activityOf(milestoneId: UUID, from: LocalDate, to: LocalDate): List[MilestoneActivity]
}

object TaskStatsSource {
  val Empty: TaskStatsSource = new TaskStatsSource {
    override def statusInUse(statusId: UUID): Boolean = false
    override def activityOf(milestoneId: UUID, from: LocalDate, to: LocalDate): List[MilestoneActivity] = List.empty
  }
}

/** Holds the [[TaskStatsSource]]; the tasks preview replaces the empty default with its own at start up. */
class TaskStatsPreviewData {
  @volatile var source: TaskStatsSource = TaskStatsSource.Empty
}
