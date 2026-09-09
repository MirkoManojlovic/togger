package com.togger.milestone

import doobie.ConnectionIO

import java.util.UUID

trait MilestoneRepo {

  def insert(milestone: Milestone): ConnectionIO[Milestone]

  def findById(id: UUID): ConnectionIO[Milestone]

  def deleteById(id: UUID): ConnectionIO[Int]

  def findByProjectId(projectId: UUID): ConnectionIO[List[Milestone]]

}
