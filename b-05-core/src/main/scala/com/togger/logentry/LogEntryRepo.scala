package com.togger.logentry

import doobie.ConnectionIO

import java.time.Instant
import java.util.UUID

trait LogEntryRepo {

  def insert(link: LogEntry): ConnectionIO[LogEntry]

  def findById(id: UUID): ConnectionIO[LogEntry]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(link: LogEntry, updatedAt: Instant): ConnectionIO[LogEntry]

  def findByTaskId(taskId: Long): ConnectionIO[List[LogEntry]]

  def deleteByTaskId(taskId: Long): ConnectionIO[Int]

}
