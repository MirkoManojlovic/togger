package com.togger.dto

import com.togger.milestone.MilestoneStepStatus

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A step created together with its milestone; the milestone id and the position come from the request context. */
case class NewMilestoneStepReq(name: String, description: String, startDate: LocalDate, dueDate: LocalDate)

/** `position` is the stage of the step: steps with the same position run in parallel, stages run in sequence. Absent
  * appends a new last stage, present inserts a new stage there and moves the later stages down. With `parallel` the
  * step joins the last stage (or the stage at `position`) instead and nothing moves.
  */
case class CreateMilestoneStepReq(
  milestoneId: UUID,
  name: String,
  description: String,
  startDate: LocalDate,
  dueDate: LocalDate,
  position: Option[Int] = None,
  parallel: Boolean = false
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

/** `stages` is the complete new order of the steps of the milestone: the outer list is the sequence of stages, the
  * steps of an inner list run in parallel.
  */
case class ReorderMilestoneStepsReq(milestoneId: UUID, stages: List[List[UUID]], changeComment: String)

/** `tasksTotal` and `tasksDone` count the tasks in the step; a task is done when it has an end date. */
case class FindMilestoneStepResp(
  id: UUID,
  milestoneId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  position: Int, // the stage: steps with the same position run in parallel
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  updatedAt: Instant,
  changeComment: String,
  tasksTotal: Int = 0,
  tasksDone: Int = 0
)

case class MilestoneStepVersionDto(
  version: UUID,
  milestoneStepId: UUID,
  name: String,
  description: String,
  status: MilestoneStepStatus,
  position: Int, // the stage: steps with the same position run in parallel
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate],
  createdAt: Instant,
  updatedAt: Instant,
  changeComment: String
)
