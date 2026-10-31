package com.togger.controllers.milestone

import com.togger.dto.{FindMilestoneStepResp, MilestoneStepVersionDto}
import com.togger.milestone.{MilestoneStep, MilestoneStepVersion, MilestoneStepWithOneVersion}

object MilestoneStepDtoMapper {

  def toDto(step: MilestoneStepWithOneVersion): FindMilestoneStepResp = toDto(step.milestoneStep, step.version)

  def toDto(step: MilestoneStep, version: MilestoneStepVersion): FindMilestoneStepResp =
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
      changeComment = version.changeComment
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
