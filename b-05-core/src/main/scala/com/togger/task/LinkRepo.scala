package com.togger.task

import doobie.ConnectionIO

import java.util.UUID

trait LinkRepo {

  def insert(link: Link): ConnectionIO[Link]

  def insertWithId(link: Link): ConnectionIO[Link]

  def findById(id: UUID): ConnectionIO[Link]

  def deleteById(id: UUID): ConnectionIO[Int]

  def insertMany(links: Seq[Link]): ConnectionIO[List[Link]]

  /** Bounded by caller: the links of one task, loaded with that task. Decided to stay unpaginated. */
  def findByTaskId(taskId: UUID): ConnectionIO[List[Link]]

  /** Every link of the project's tasks, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[Link]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
