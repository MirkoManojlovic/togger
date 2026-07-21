package com.togger.task

import com.togger.account.Account

import java.time.Instant
import java.util.UUID

case class Task(
  id: UUID,
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  closedBy: Option[UUID] = None,
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant
) {
  def toFull(
    createdBy: Account,
    assignedToOpt: Option[Account],
    closedByOpt: Option[Account],
    taskStatus: TaskStatus
  ): TaskFull =
    TaskFull(
      id = id,
      projectId = projectId,
      name = name,
      description = description,
      assignedTo = assignedToOpt,
      createdBy = createdBy,
      closedBy = closedByOpt,
      taskStatus = taskStatus,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
}

case class TaskFull(
  id: UUID,
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[Account],
  createdBy: Account,
  closedBy: Option[Account] = None,
  taskStatus: TaskStatus,
  createdAt: Instant,
  updatedAt: Instant
) {
  lazy val asTask: Task =
    Task(
      id = id,
      projectId = projectId,
      name = name,
      description = description,
      assignedTo = assignedTo.map(_.id),
      createdBy = createdBy.id,
      closedBy = closedBy.map(_.id),
      taskStatusId = taskStatus.id,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
}

case class TaskStatus(
  id: UUID,
  projectId: Long,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)
