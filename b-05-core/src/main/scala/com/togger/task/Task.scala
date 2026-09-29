package com.togger.task

import com.togger.account.Account

import java.time.Instant
import java.util.UUID

case class Task(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant)

case class TaskVersion(
  version: UUID,
  taskId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  closedBy: Option[UUID] = None,
  taskStatusId: UUID,
  milestone: Option[UUID] = None,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(
    name: String,
    description: String,
    assignedTo: Option[UUID],
    closedBy: Option[UUID],
    taskStatusId: UUID,
    milestone: Option[UUID] = None
  ): TaskVersion =
    this.copy(
      name = name,
      description = description,
      assignedTo = assignedTo,
      closedBy = closedBy,
      taskStatusId = taskStatusId,
      milestone = milestone
    )

  def hasSameContentAs(other: TaskVersion): Boolean =
    name == other.name &&
      description == other.description &&
      assignedTo == other.assignedTo &&
      closedBy == other.closedBy &&
      taskStatusId == other.taskStatusId &&
      milestone == other.milestone

}

case class TaskWithOneVersion(task: Task, version: TaskVersion)
case class TaskWithVersions(task: Task, versions: Seq[TaskVersion]) {
  lazy val latestVersion: TaskVersion = versions.head
}

case class TaskFull(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[Account],
  createdBy: Account,
  closedBy: Option[Account] = None,
  taskStatus: TaskStatus,
  taskLabels: Seq[TaskLabel] = Seq.empty,
  milestone: Option[UUID] = None,
  createdAt: Instant,
  updatedAt: Instant,
  latestVersion: UUID
)

case class TaskStatus(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class TaskLabel(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class TaskLabelAssignment(taskId: UUID, taskLabelId: UUID, createdAt: Instant)

