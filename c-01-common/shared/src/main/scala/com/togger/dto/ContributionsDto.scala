package com.togger.dto

import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus

import java.time.LocalDate
import java.util.UUID

case class StatusCountDto(status: LogEntryStatus, count: Int)

case class DailyCountDto(date: LocalDate, count: Int)

/** What one member contributed in some scope: the whole project or a single milestone. Holiday and vacation entries are
  * listed under `entriesByStatus` but are not work: they are left out of `activeDays` and `dailyEntries`.
  */
case class MemberContributionDto(
  memberId: UUID,
  memberName: String,
  entriesByStatus: List[StatusCountDto],
  tasksAssigned: Int,
  tasksClosed: Int,
  activeDays: Int,
  workDaysInRange: Int,
  dailyEntries: List[DailyCountDto]
)

/** The contributions to one milestone; `milestoneId` is empty for the tasks that have no milestone. */
case class MilestoneContributionsDto(
  milestoneId: Option[UUID],
  name: String,
  status: Option[MilestoneStatus],
  startDate: Option[LocalDate],
  dueDate: Option[LocalDate],
  totalTasks: Int,
  closedTasks: Int,
  members: List[MemberContributionDto]
)

case class ContributionsDto(
  projectId: UUID,
  general: List[MemberContributionDto],
  milestones: List[MilestoneContributionsDto]
)
