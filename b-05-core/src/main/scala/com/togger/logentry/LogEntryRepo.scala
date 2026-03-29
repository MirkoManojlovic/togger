package com.togger.logentry

import doobie.ConnectionIO

import java.time.Instant

trait LogEntryRepo {

  def insert(link: LogEntry): ConnectionIO[LogEntry]

  def findById(id: Long): ConnectionIO[LogEntry]

  def deleteById(id: Long): ConnectionIO[Int]

  def update(link: LogEntry, updatedAt: Instant): ConnectionIO[LogEntry]

  def findByTaskId(taskId: Long): ConnectionIO[List[LogEntry]]

  def deleteByTaskId(taskId: Long): ConnectionIO[Int]

}
