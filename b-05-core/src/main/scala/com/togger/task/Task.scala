package com.togger.task

import com.togger.account.Account

import java.time.Instant

case class Task(
  id: Long,
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[Long],
  createdBy: Long,
  closedBy: Option[Long] = None,
  taskStatusId: Long,
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
  id: Long,
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
  id: Long,
  projectId: Long,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)
