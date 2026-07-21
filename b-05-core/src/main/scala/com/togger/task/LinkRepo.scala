package com.togger.task

import doobie.ConnectionIO

import java.time._
import java.util.UUID

trait LinkRepo {

  def insert(link: Link): ConnectionIO[Link]

  def findById(id: UUID): ConnectionIO[Link]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(link: Link, updatedAt: Instant): ConnectionIO[Link]

  def insertMany(links: Seq[Link]): ConnectionIO[List[Link]]

  def findByTaskId(taskId: UUID): ConnectionIO[List[Link]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
