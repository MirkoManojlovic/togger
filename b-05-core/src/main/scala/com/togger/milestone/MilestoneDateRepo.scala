package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneDateRepo {

  def insert(milestoneDate: MilestoneDate): ConnectionIO[MilestoneDate]
  def insertWithId(milestoneDate: MilestoneDate): ConnectionIO[MilestoneDate]

  def findById(id: UUID): ConnectionIO[MilestoneDate]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Bounded by caller: the dates of one milestone, read when that single milestone is opened. */
  def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneDate]]

  def findPaginatedByProjectId(
    projectId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneDate]]

}
