package com.togger.task

import doobie.ConnectionIO

import java.util.UUID

trait LinkVersionRepo {

  def insert(linkVersion: LinkVersion): ConnectionIO[LinkVersion]

  def insertWithId(linkVersion: LinkVersion): ConnectionIO[LinkVersion]

  def findByVersion(version: UUID): ConnectionIO[LinkVersion]

  /** Bounded by caller: the history of one link, loaded with its task. Decided to stay unpaginated. */
  def findByLinkId(linkId: UUID): ConnectionIO[List[LinkVersion]]

  /** Every link version of the project's tasks, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[LinkVersion]]

  def findLatestByLinkId(linkId: UUID): ConnectionIO[LinkVersion]

  def updateContent(linkVersion: LinkVersion): ConnectionIO[LinkVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByLinkId(linkId: UUID): ConnectionIO[Int]

}
