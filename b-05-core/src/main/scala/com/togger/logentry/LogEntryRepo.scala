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

  /** Bounded by caller: the log entries of one task, loaded with that task by the page assembly and the disk dump. The
    * endpoint lists them with [[findPaginatedByTaskId]].
    */
  def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]]

  /** Every log entry of the project's tasks, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[LogEntry]]

  def findPaginatedByTaskId(taskId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[LogEntry]]

  /** One page of the project's log entries, newest first. */
  def findPaginatedByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntry]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
