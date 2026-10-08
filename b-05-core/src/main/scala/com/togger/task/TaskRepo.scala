package com.togger.task

import com.togger.common.{DbPageRequest, PaginatedResponse}
import com.togger.dto.TaskFilter
import doobie.ConnectionIO

import java.time._
import java.util.UUID

trait TaskRepo {

  def findById(id: UUID): ConnectionIO[Task]

  def insert(task: Task): ConnectionIO[Task]

  def insertWithId(task: Task): ConnectionIO[Task]

  def deleteById(id: UUID): ConnectionIO[Int]

}

trait TaskVersionRepo {

  def insert(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def insertWithId(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def findByVersion(version: UUID): ConnectionIO[TaskVersion]

  /** Bounded by caller: the history of one task, written out as a unit by the disk dump. The endpoint lists it with
    * [[findPaginatedByTaskId]], newest version first.
    */
  def findByTaskId(taskId: UUID): ConnectionIO[List[TaskVersion]]

  def findPaginatedByTaskId(taskId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TaskVersion]]

  def findLatestByTaskId(taskId: UUID): ConnectionIO[TaskVersion]

  def updateContent(taskVersion: TaskVersion): ConnectionIO[TaskVersion]

  def deleteByVersion(version: UUID): ConnectionIO[Int]

  def countByTaskId(taskId: UUID): ConnectionIO[Int]

  def findByIdWithLatest(taskId: UUID): ConnectionIO[TaskFull]

  /** Every task of the project, unbounded: only for whole-project jobs such as the disk dump, never for an endpoint. */
  def findAllByProjectIdWithLatest(projectId: UUID): ConnectionIO[List[TaskFull]]

  /** One page of the project's tasks with their latest version, newest update first. */
  def findPaginatedByProjectId(
    projectId: UUID,
    filter: TaskFilter,
    page: DbPageRequest
  ): ConnectionIO[PaginatedResponse[TaskFull]]

}

trait TaskStatusRepo {

  def findById(id: UUID): ConnectionIO[TaskStatus]

  def insert(task: TaskStatus): ConnectionIO[TaskStatus]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(task: TaskStatus, updatedAt: Instant): ConnectionIO[TaskStatus]

  /** One page of the project's statuses, by name. */
  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TaskStatus]]

}

trait TaskLabelRepo {

  def findById(id: UUID): ConnectionIO[TaskLabel]

  def insert(taskLabel: TaskLabel): ConnectionIO[TaskLabel]

  def deleteById(id: UUID): ConnectionIO[Int]

  def update(taskLabel: TaskLabel, updatedAt: Instant): ConnectionIO[TaskLabel]

  /** One page of the project's labels, by name. */
  def findPaginatedByProjectId(projectId: UUID, page: DbPageRequest): ConnectionIO[PaginatedResponse[TaskLabel]]

}

trait TaskLabelAssignmentRepo {

  def insert(assignment: TaskLabelAssignment): ConnectionIO[TaskLabelAssignment]

  def deleteAllByTaskId(taskId: UUID): ConnectionIO[Int]

  /** Bounded by caller: the labels assigned to one task, embedded in that task's payload. */
  def findLabelsByTaskId(taskId: UUID): ConnectionIO[List[TaskLabel]]

  /** Bounded by caller (the ids come from a page of tasks): the labels assigned to each of them. */
  def findLabelsByTaskIds(taskIds: Seq[UUID]): ConnectionIO[Map[UUID, List[TaskLabel]]]

}
