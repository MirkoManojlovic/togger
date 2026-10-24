package com.togger.timebox

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.task.TaskVersion
import doobie.ConnectionIO

import java.time.LocalDate
import java.util.UUID

/** Timebox versions are append-only: there is intentionally no update or delete by version. */
trait TimeboxVersionRepo {

  def insert(timeboxVersion: TimeboxVersion): ConnectionIO[TimeboxVersion]

  def insertWithId(timeboxVersion: TimeboxVersion): ConnectionIO[TimeboxVersion]

  def insertPlannedTasks(timeboxVersion: UUID, taskVersions: Seq[UUID]): ConnectionIO[Int]

  def findByVersion(version: UUID): ConnectionIO[TimeboxVersion]

  def findPaginatedByTimeboxId(timeboxId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TimeboxVersion]]

  def findLatestByTimeboxId(timeboxId: UUID): ConnectionIO[TimeboxVersion]

  /** Whether another timebox of the project has a latest version whose inclusive date range intersects the given one. */
  def existsOverlappingLatest(
    projectId: UUID,
    startDate: LocalDate,
    endDate: LocalDate,
    excludedTimeboxId: Option[UUID]
  ): ConnectionIO[Boolean]

  /** Bounded by caller: the tasks planned in one timebox version, embedded in that version's payload. */
  def findPlannedTasks(timeboxVersion: UUID): ConnectionIO[List[TaskVersion]]

}
