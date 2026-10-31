package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneStepRepo {

  def insert(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep]
  def insertWithId(milestoneStep: MilestoneStep): ConnectionIO[MilestoneStep]

  def findById(id: UUID): ConnectionIO[MilestoneStep]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Not paginated on purpose: the steps of one milestone are a short, user maintained list. */
  def findAllByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneStep]]

}
