package com.togger.contribution

import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.task.TaskPriority

import java.time.LocalDate
import java.util.UUID

/** Aggregated rows of the contribution queries. An empty `milestoneId` means the tasks without a milestone. */
case class EntryActivityRow(
  memberId: UUID,
  milestoneId: Option[UUID],
  day: LocalDate,
  status: LogEntryStatus,
  count: Long
)

case class TaskAssignedRow(memberId: UUID, milestoneId: Option[UUID], count: Long)

case class TaskClosedRow(memberId: UUID, milestoneId: Option[UUID], count: Long)

case class MilestoneTaskTotalsRow(milestoneId: Option[UUID], total: Long, closed: Long)

/** The number of tasks, by the attribute of their latest version, in a milestone or in none, and by who they are
  * assigned to, or to nobody.
  */
case class TaskStatusCountRow(milestoneId: Option[UUID], assigneeId: Option[UUID], statusName: String, count: Long)

case class TaskPriorityCountRow(
  milestoneId: Option[UUID],
  assigneeId: Option[UUID],
  priority: TaskPriority,
  count: Long
)

/** A task counts once for every label it has, and once with no label name if it has none. */
case class TaskLabelCountRow(
  milestoneId: Option[UUID],
  assigneeId: Option[UUID],
  labelName: Option[String],
  count: Long
)

/** The latest version of a milestone, as shown on its card. */
case class MilestoneHeaderRow(
  milestoneId: UUID,
  name: String,
  status: MilestoneStatus,
  startDate: LocalDate,
  dueDate: LocalDate
)
