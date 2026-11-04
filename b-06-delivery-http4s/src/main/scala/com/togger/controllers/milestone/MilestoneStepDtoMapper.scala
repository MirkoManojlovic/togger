package com.togger.controllers.milestone

import com.togger.dto.FindMilestoneStepResp
import com.togger.dto.MilestoneStepVersionDto
import com.togger.milestone.MilestoneStep
import com.togger.milestone.MilestoneStepTaskCounts
import com.togger.milestone.MilestoneStepVersion
import com.togger.milestone.MilestoneStepWithOneVersion

object MilestoneStepDtoMapper {

  def toDto(
    step: MilestoneStepWithOneVersion,
    taskCounts: MilestoneStepTaskCounts = MilestoneStepTaskCounts.empty
  ): FindMilestoneStepResp = toDto(step.milestoneStep, step.version, taskCounts)

  def toDto(
    step: MilestoneStep,
    version: MilestoneStepVersion,
    taskCounts: MilestoneStepTaskCounts
  ): FindMilestoneStepResp =
    FindMilestoneStepResp(
      id = step.id,
      milestoneId = step.milestoneId,
      createdAt = step.createdAt,
      latestVersion = version.version,
      name = version.name,
      description = version.description,
      status = version.status,
      position = version.position,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      updatedAt = version.updatedAt,
      changeComment = version.changeComment,
      tasksTotal = taskCounts.total,
      tasksDone = taskCounts.done
    )

  def toVersionDto(version: MilestoneStepVersion): MilestoneStepVersionDto =
    MilestoneStepVersionDto(
      version = version.version,
      milestoneStepId = version.milestoneStepId,
      name = version.name,
      description = version.description,
      status = version.status,
      position = version.position,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt,
      changeComment = version.changeComment
    )

}
