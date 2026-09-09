package com.togger.controllers.milestone

import com.togger.dto.{FindMilestoneDateResp, MilestoneDateVersionDto}
import com.togger.milestone.{MilestoneDate, MilestoneDateVersion}

object MilestoneDateDtoMapper {

  def toDto(milestoneDate: MilestoneDate, version: MilestoneDateVersion): FindMilestoneDateResp =
    FindMilestoneDateResp(
      id = milestoneDate.id,
      milestoneId = milestoneDate.milestoneId,
      createdAt = milestoneDate.createdAt,
      latestVersion = version.version,
      name = version.name,
      description = version.description,
      date = version.date,
      updatedAt = version.updatedAt
    )

  def toVersionDto(version: MilestoneDateVersion): MilestoneDateVersionDto =
    MilestoneDateVersionDto(
      version = version.version,
      milestoneDateId = version.milestoneDateId,
      name = version.name,
      description = version.description,
      date = version.date,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
