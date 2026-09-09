package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneVersionRepo {

  def insert(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion]

  def findByVersion(version: UUID): ConnectionIO[MilestoneVersion]

  def findByMilestoneId(milestoneId: UUID): ConnectionIO[List[MilestoneVersion]]

  def findLatestByMilestoneId(milestoneId: UUID): ConnectionIO[MilestoneVersion]

  def updateContent(milestoneVersion: MilestoneVersion): ConnectionIO[MilestoneVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByMilestoneId(milestoneId: UUID): ConnectionIO[Int]

}
