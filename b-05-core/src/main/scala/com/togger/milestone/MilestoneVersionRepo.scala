package com.togger.milestone

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneVersionRepo {

  def insert(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneVersion]

  def findPaginatedByMilestoneId(
    milestoneId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneVersion]]

  def findLatestByMilestoneId(milestoneId: UUID): ConnectionIO[MilestoneVersion]

  def updateContent(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByMilestoneId(milestoneId: UUID): ConnectionIO[Int]

}
