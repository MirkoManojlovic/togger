package com.togger.dto

import com.togger.logentry.LogEntryStatus
import com.togger.milestone.MilestoneStatus
import com.togger.task.TaskPriority

import java.time.LocalDate
import java.util.UUID

case class StatusCountDto(status: LogEntryStatus, count: Int)

case class DailyStatusCountDto(date: LocalDate, status: LogEntryStatus, count: Int)

case class TaskStatusCountDto(name: String, count: Int)

case class TaskPriorityCountDto(priority: TaskPriority, count: Int)

/** An empty name is the count of the tasks without a label. */
case class TaskLabelCountDto(name: Option[String], count: Int)

/** The tasks of a scope, counted by the attributes of their latest version. A task is counted once for every label. */
case class TaskAttributesDto(
  byStatus: List[TaskStatusCountDto],
  byPriority: List[TaskPriorityCountDto],
  byLabel: List[TaskLabelCountDto]
)

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
  dailyEntries: List[DailyStatusCountDto]
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
  members: List[MemberContributionDto],
  tasks: TaskAttributesDto
)

case class ContributionsDto(
  projectId: UUID,
  general: List[MemberContributionDto],
  generalTasks: TaskAttributesDto,
  milestones: List[MilestoneContributionsDto]
)
