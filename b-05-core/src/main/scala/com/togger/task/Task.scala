package com.togger.task

import com.togger.account.Account

import java.time.{Instant, LocalDate}
import java.util.UUID

case class Task(id: UUID, projectId: UUID, createdBy: UUID, createdAt: Instant, archived: Boolean = false)

case class TaskVersion(
  version: UUID,
  taskId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  closedBy: Option[UUID] = None,
  taskStatusId: UUID,
  priority: TaskPriority = TaskPriority.Medium,
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None,
  createdAt: Instant,
  updatedAt: Instant
) {

  def withNewContent(
    name: String,
    description: String,
    assignedTo: Option[UUID],
    closedBy: Option[UUID],
    taskStatusId: UUID,
    priority: TaskPriority,
    milestone: Option[UUID] = None,
    startDate: LocalDate,
    dueDate: LocalDate,
    endDate: Option[LocalDate] = None
  ): TaskVersion =
    this.copy(
      name = name,
      description = description,
      assignedTo = assignedTo,
      closedBy = closedBy,
      taskStatusId = taskStatusId,
      priority = priority,
      milestone = milestone,
      startDate = startDate,
      dueDate = dueDate,
      endDate = endDate
    )

  def hasSameContentAs(other: TaskVersion): Boolean =
    name == other.name &&
      description == other.description &&
      assignedTo == other.assignedTo &&
      closedBy == other.closedBy &&
      taskStatusId == other.taskStatusId &&
      priority == other.priority &&
      milestone == other.milestone &&
      startDate == other.startDate &&
      dueDate == other.dueDate &&
      endDate == other.endDate

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
  priority: TaskPriority,
  taskLabels: Seq[TaskLabel] = Seq.empty,
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None,
  createdAt: Instant,
  updatedAt: Instant,
  latestVersion: UUID,
  archived: Boolean = false
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

