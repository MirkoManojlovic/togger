package com.togger.projectmapping

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait ProjectMappingRepo {

  def findById(id: UUID): ConnectionIO[ProjectMapping]

  def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(projectMapping: ProjectMapping, updatedAt: Instant): ConnectionIO[ProjectMapping]

  def findByProjectIdLoaded(projectIdLoaded: UUID): ConnectionIO[List[ProjectMapping]]

}
