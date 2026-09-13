package com.togger.logentry

import doobie.ConnectionIO

import java.util.UUID

trait LogEntryRepo {

  def insert(logEntry: LogEntry): ConnectionIO[LogEntry]

  def insertWithId(logEntry: LogEntry): ConnectionIO[LogEntry]

  def findById(id: UUID): ConnectionIO[LogEntry]

  def deleteById(id: UUID): ConnectionIO[Int]

  def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]]

  def findByProjectId(projectId: UUID): ConnectionIO[List[LogEntry]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
