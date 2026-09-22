package com.togger.controllers.milestone

import com.togger.dto.{FindMilestoneResp, MilestoneVersionDto}
import com.togger.milestone.{Milestone, MilestoneVersion}

object MilestoneDtoMapper {

  def toDto(milestone: Milestone, version: MilestoneVersion): FindMilestoneResp =
    FindMilestoneResp(
      id = milestone.id,
      projectId = milestone.projectId,
      createdAt = milestone.createdAt,
      latestVersion = version.version,
      name = version.name,
      description = version.description,
      status = version.status,
      startDate = version.startDate,
      estimatedEndDate = version.estimatedEndDate,
      endDate = version.endDate,
      updatedAt = version.updatedAt
    )

  def toVersionDto(version: MilestoneVersion): MilestoneVersionDto =
    MilestoneVersionDto(
      version = version.version,
      milestoneId = version.milestoneId,
      name = version.name,
      description = version.description,
      status = version.status,
      startDate = version.startDate,
      estimatedEndDate = version.estimatedEndDate,
      endDate = version.endDate,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
