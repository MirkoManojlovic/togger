package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneSummaryRepo {

  def insert(milestoneSummary: MilestoneSummary): ConnectionIO[MilestoneSummary]
  def insertWithId(milestoneSummary: MilestoneSummary): ConnectionIO[MilestoneSummary]

  def findById(id: UUID): ConnectionIO[MilestoneSummary]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Bounded by caller: the dates of one milestone, read when that single milestone is opened. */
  def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummary]]

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneSummary]]

}
