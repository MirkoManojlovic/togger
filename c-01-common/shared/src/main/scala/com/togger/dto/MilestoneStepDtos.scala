package com.togger.dto

import com.togger.milestone.MilestoneStepStatus

import java.time.{Instant, LocalDate}
import java.util.UUID

/** A step created together with its milestone; the milestone id and the position come from the request context. */
case class NewMilestoneStepReq(name: String, description: String, startDate: LocalDate, dueDate: LocalDate)

/** `position` is zero based; absent appends the step, present inserts it and moves the later steps down. */
case class CreateMilestoneStepReq(
  milestoneId: UUID,
  name: String,
  description: String,
  startDate: LocalDate,
  dueDate: LocalDate,
  position: Option[Int] = None
)

/** Replaces the whole content; an absent `endDate` clears it (e.g. when a closed step is reopened). */
case class UpdateMilestoneStepReq(
  id: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  changeComment: String
)

/** `stepIds` is the complete new order of the steps of the milestone. */
case class ReorderMilestoneStepsReq(milestoneId: UUID, stepIds: List[UUID], changeComment: String)

/** `tasksTotal` and `tasksDone` count the tasks in the step; a task is done when it has an end date. */
case class FindMilestoneStepResp(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  position: Int,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  updatedAt: Instant,
  changeComment: String,
  tasksTotal: Int = 0,
  tasksDone: Int = 0,
  shelf: Boolean = false
)

case class MilestoneStepVersionDto(
  version: UUID,
  milestoneStepId: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  position: Int,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  createdAt: Instant,
  updatedAt: Instant,
  changeComment: String
)
