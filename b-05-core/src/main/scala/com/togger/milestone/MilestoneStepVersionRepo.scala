package com.togger.milestone

import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import doobie.ConnectionIO

import java.util.UUID

trait MilestoneStepVersionRepo {

  def insert(milestoneStepVersion: MilestoneStepVersion): ConnectionIO[MilestoneStepVersion]
  def insertWithId(milestoneStepVersion: MilestoneStepVersion): ConnectionIO[MilestoneStepVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneStepVersion]

  def findPaginatedByMilestoneStepId(
    milestoneStepId: UUID,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[MilestoneStepVersion]]

  def findLatestByMilestoneStepId(milestoneStepId: UUID): ConnectionIO[MilestoneStepVersion]

  /** The latest version of every step of the milestone, in no particular order. */
  def findLatestByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneStepVersion]]

  /** The latest version of every step of every milestone of the project, in no particular order. */
  def findLatestByProjectId(projectId: UUID): ConnectionIO[List[MilestoneStepVersion]]

  /** Counts of the latest versions per milestone; milestones without steps are absent from the result. */
  def countByMilestoneIds(milestoneIds: List[UUID]): ConnectionIO[Map[UUID, MilestoneStepCounts]]

}
