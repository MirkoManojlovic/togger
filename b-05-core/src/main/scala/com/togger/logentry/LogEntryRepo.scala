package com.togger.logentry

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.dto.LogEntryFilter
import doobie.ConnectionIO

import java.util.UUID

trait LogEntryRepo {

  def insert(logEntry: LogEntry): ConnectionIO[LogEntry]

  def insertWithId(logEntry: LogEntry): ConnectionIO[LogEntry]

  def findById(id: UUID): ConnectionIO[LogEntry]

  def deleteById(id: UUID): ConnectionIO[Int]

  def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]]

  /** One page of the project's log entries, newest first. */
  def findPaginatedByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntry]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
