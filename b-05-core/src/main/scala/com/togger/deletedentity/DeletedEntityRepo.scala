package com.togger.deletedentity

import doobie.ConnectionIO

import java.util.UUID

trait DeletedEntityRepo {

  /** Records the deletions; deleting an entity again keeps the newest deletion time. */
  def insertAll(deletedEntities: List[DeletedEntity]): ConnectionIO[Int]

  /** Every tombstone of the project, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[DeletedEntity]]

  def deleteByEntity(projectId: UUID, entityType: String, entityId: UUID): ConnectionIO[Int]

}
