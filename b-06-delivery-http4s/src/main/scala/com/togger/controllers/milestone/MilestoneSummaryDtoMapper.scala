package com.togger.controllers.milestone

import com.togger.dto.{FindMilestoneSummaryResp, MilestoneSummaryVersionDto}
import com.togger.milestone.{MilestoneSummary, MilestoneSummaryVersion}

object MilestoneSummaryDtoMapper {

  def toDto(milestoneSummary: MilestoneSummary, version: MilestoneSummaryVersion): FindMilestoneSummaryResp =
    FindMilestoneSummaryResp(
      id = milestoneSummary.id,
      milestoneId = milestoneSummary.milestoneId,
      createdAt = milestoneSummary.createdAt,
      latestVersion = version.version,
      summary = version.summary,
      startDate = version.startDate,
      endDate = version.endDate,
      updatedAt = version.updatedAt
    )

  def toVersionDto(version: MilestoneSummaryVersion): MilestoneSummaryVersionDto =
    MilestoneSummaryVersionDto(
      version = version.version,
      milestoneSummaryId = version.milestoneSummaryId,
      summary = version.summary,
      startDate = version.startDate,
      endDate = version.endDate,
      createdAt = version.createdAt,
      updatedAt = version.updatedAt
    )

}
