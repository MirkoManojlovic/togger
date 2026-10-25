package com.togger.logentry

import com.togger.common.{DbPageRequest, PaginatedResponse}
import doobie.ConnectionIO

import java.util.UUID

trait LogEntryVersionRepo {

  def insert(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion]

  def insertWithId(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion]

  def findByVersion(version: UUID): ConnectionIO[LogEntryVersion]

  /** Bounded by caller: the history of one log entry, written out as a unit by the disk dump. The endpoint lists it
    * with [[findPaginatedByLogEntryId]].
    */
  def findByLogEntryId(logEntryId: UUID): ConnectionIO[List[LogEntryVersion]]

  /** Every log entry version of the project's tasks, unbounded: only for whole-project jobs such as the disk dump. */
  def findAllByProjectId(projectId: UUID): ConnectionIO[List[LogEntryVersion]]

  def findPaginatedByLogEntryId(logEntryId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[LogEntryVersion]]

  def findLatestByLogEntryId(logEntryId: UUID): ConnectionIO[LogEntryVersion]

  def updateContent(logEntryVersion: LogEntryVersion): ConnectionIO[LogEntryVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByLogEntryId(logEntryId: UUID): ConnectionIO[Int]

}
