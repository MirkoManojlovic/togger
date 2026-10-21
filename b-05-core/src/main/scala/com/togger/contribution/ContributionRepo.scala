package com.togger.contribution

import com.togger.dto.ContributionsFilter
import doobie.ConnectionIO

import java.util.UUID

/** Read-only aggregates behind the contributions page. Every row carries its milestone, so the same rows give the
  * project-wide numbers (summed over milestones) and the per-milestone ones.
  */
trait ContributionRepo {

  /** Log entries by author, milestone of the task, UTC day and current status; `created_at` is filtered by the range.
    */
  def entryActivity(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[EntryActivityRow]]

  /** Tasks whose latest version is assigned to someone, filtered by the start date of that version. */
  def tasksAssigned(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskAssignedRow]]

  /** Tasks whose latest version has a closer, filtered by its end date.
    */
  def tasksClosed(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskClosedRow]]

  /** Tasks by the status of their latest version, filtered by the start date of that version. */
  def tasksByStatus(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskStatusCountRow]]

  def tasksByPriority(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskPriorityCountRow]]

  def tasksByLabel(projectId: UUID, filter: ContributionsFilter): ConnectionIO[List[TaskLabelCountRow]]

  /** Every milestone of the project with its latest version, the one created last first. */
  def milestoneHeaders(projectId: UUID): ConnectionIO[List[MilestoneHeaderRow]]

  /** All tasks and ended tasks per milestone, ignoring the range. */
  def milestoneTaskTotals(projectId: UUID): ConnectionIO[List[MilestoneTaskTotalsRow]]

}
