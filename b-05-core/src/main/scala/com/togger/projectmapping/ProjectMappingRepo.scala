package com.togger.projectmapping

import doobie.ConnectionIO

import java.util.UUID

trait ProjectMappingRepo {

  def insert(projectMapping: ProjectMapping): ConnectionIO[ProjectMapping]

  def findByProjectIdDb(projectIdDb: UUID): ConnectionIO[List[ProjectMapping]]

  def findByProjectIdDump(projectIdDump: UUID): ConnectionIO[List[ProjectMapping]]

  def deleteByIds(projectIdDb: UUID, projectIdDump: UUID): ConnectionIO[Int]

}
