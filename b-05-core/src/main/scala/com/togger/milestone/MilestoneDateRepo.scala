package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneDateRepo {

  def insert(milestoneDate: MilestoneDate): ConnectionIO[MilestoneDate]

  def findById(id: UUID): ConnectionIO[MilestoneDate]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Bounded by caller: the dates of one milestone, read when that single milestone is opened. */
  def findByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneDate]]

  def findByProjectId(projectId: UUID): ConnectionIO[List[MilestoneDate]]

}
