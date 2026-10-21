package com.togger.contribution

import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus

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

/** The latest version of a milestone, as shown on its card. */
case class MilestoneHeaderRow(
  milestoneId: UUID,
  name: String,
  status: MilestoneStatus,
  startDate: LocalDate,
  dueDate: LocalDate
)
