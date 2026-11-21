package com.togger.logentry

import com.togger.common.DbPageRequest
import com.togger.common.PaginatedResponse
import com.togger.dto.LogEntryFilter
import doobie.ConnectionIO

import java.time.LocalDate
import java.util.UUID

trait LogEntryRepo {

  def insert(logEntry: LogEntry): ConnectionIO[LogEntry]

  def insertWithId(logEntry: LogEntry): ConnectionIO[LogEntry]

  def findById(id: UUID): ConnectionIO[LogEntry]

  def findOptionById(id: UUID): ConnectionIO[Option[LogEntry]]

  /** Whether the task has a log entry for `logDate`. */
  def existsByTaskIdAndLogDate(taskId: UUID, logDate: LocalDate): ConnectionIO[Boolean]

  /** Whether the task has a log entry for `logDate` other than `excludedId`: the check for moving an entry. */
  def existsByTaskIdAndLogDateExcluding(taskId: UUID, logDate: LocalDate, excludedId: UUID): ConnectionIO[Boolean]

  def updateLogDate(id: UUID, logDate: LocalDate): ConnectionIO[Int]

  def deleteById(id: UUID): ConnectionIO[Int]

  /** Bounded by caller: the log entries of one task, loaded with that task by the page assembly and the disk dump. The
    * endpoint lists them with [[findPaginatedByTaskId]].
    */
  def findByTaskId(taskId: UUID): ConnectionIO[List[LogEntry]]

  /** Every log entry of the project's tasks, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[LogEntry]]

  def findPaginatedByTaskId(taskId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[LogEntry]]

  /** One page of the project's log entries, newest log date first. */
  def findPaginatedByProjectId(
    projectId: UUID,
    filter: LogEntryFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[LogEntry]]

  /** Bounded by caller (`limit`): the latest version of every log entry of the milestone's tasks whose log date is in
    * `[from, to]` (both inclusive), oldest first.
    */
  def findLatestByMilestoneIdBetween(
    milestoneId: UUID,
    from: LocalDate,
    to: LocalDate,
    limit: Int
  ): ConnectionIO[List[LogEntryWithOneVersion]]

  def deleteByTaskId(taskId: UUID): ConnectionIO[Int]

}
