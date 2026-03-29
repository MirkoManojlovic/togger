package com.togger.task

import doobie.ConnectionIO

import java.time._

trait LinkRepo {

  def insert(link: Link): ConnectionIO[Link]

  def findById(id: Long): ConnectionIO[Link]

  def deleteById(id: Long): ConnectionIO[Int]

  def update(link: Link, updatedAt: Instant): ConnectionIO[Link]

  def insertMany(links: Seq[Link]): ConnectionIO[List[Link]]

  def findByTaskId(taskId: Long): ConnectionIO[List[Link]]

  def deleteByTaskId(taskId: Long): ConnectionIO[Int]

}
