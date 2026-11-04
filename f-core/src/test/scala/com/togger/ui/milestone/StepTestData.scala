package com.togger.ui.milestone

import com.togger.dto.FindMilestoneStepResp
import com.togger.milestone.MilestoneStepStatus

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object StepTestData {

  def step(
    milestoneId: UUID,
    name: String = "Design",
    position: Int = 0,
    status: MilestoneStepStatus = MilestoneStepStatus.Planned,
    tasksTotal: Int = 0,
    tasksDone: Int = 0
  ): FindMilestoneStepResp =
    FindMilestoneStepResp(
      id = UUID.randomUUID(),
      milestoneId = milestoneId,
      createdAt = Instant.EPOCH,
      latestVersion = UUID.randomUUID(),
      name = name,
      description = "",
      status = status,
      position = position,
      startDate = LocalDate.of(2026, 10, 12),
      dueDate = LocalDate.of(2026, 11, 12),
      endDate = Option.when(status.isClosing)(LocalDate.of(2026, 11, 1)),
      updatedAt = Instant.EPOCH,
      changeComment = "",
      tasksTotal = tasksTotal,
      tasksDone = tasksDone
    )
}
