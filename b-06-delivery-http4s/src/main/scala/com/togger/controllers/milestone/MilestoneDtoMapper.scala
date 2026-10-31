package com.togger.controllers.milestone

import com.togger.dto.FindMilestoneResp
import com.togger.dto.MilestoneVersionDto
import com.togger.milestone.Milestone
import com.togger.milestone.MilestoneVersion
import com.togger.milestone.MilestoneStepCounts

object MilestoneDtoMapper {

  def toDto(
    milestone: Milestone,
    version: MilestoneVersion,
    stepCounts: MilestoneStepCounts = MilestoneStepCounts.empty
  ): FindMilestoneResp =
    FindMilestoneResp(
      id = milestone.id,
      projectId = milestone.projectId,
      createdAt = milestone.createdAt,
      latestVersion = version.version,
      name = version.name,
      description = version.description,
      status = version.status,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      updatedAt = version.updatedAt,
      stepsTotal = stepCounts.total,
      stepsClosed = stepCounts.closed
    )

  def toVersionDto(version: MilestoneVersion): MilestoneVersionDto =
    MilestoneVersionDto(
      version = version.version,
      milestoneId = version.milestoneId,
      name = version.name,
      description = version.description,
      status = version.status,
      startDate = version.startDate,
      dueDate = version.dueDate,
      endDate = version.endDate,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
