package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.dto.MilestoneFilter
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneRepo {

  def insert(milestone: Milestone): ConnectionIO[Milestone]

  def findById(id: UUID): ConnectionIO[Milestone]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** One page of the project's milestones whose latest version matches the filter, the one due first first. */
  def findPaginatedByProjectId(
    projectId: UUID,
    filter: MilestoneFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[Milestone]]

}
