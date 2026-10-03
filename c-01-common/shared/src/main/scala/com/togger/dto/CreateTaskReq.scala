package com.togger.dto

import com.togger.logentry.LogEntryStatus
import com.togger.task.TaskPriority

import java.time.{Instant, LocalDate}
import java.util.UUID

case class CreateTaskReq(
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant,
  priority: TaskPriority = TaskPriority.Medium,
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  taskLabelIds: Seq[UUID] = Seq.empty
)

case class UpdateTaskReq1(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant,
  priority: TaskPriority = TaskPriority.Medium,
  milestone: Option[UUID] = None,
  startDate: Option[LocalDate] = None,
  dueDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  taskLabelIds: Seq[UUID] = Seq.empty
)

case class FindTaskResp1(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant,
  latestVersion: UUID,
  priority: TaskPriority = TaskPriority.Medium,
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None,
  taskLabelIds: Seq[UUID] = Seq.empty
)

case class FindTaskRespFull(
  id: UUID,
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[SearchAccountDto],
  createdBy: SearchAccountDto,
  closedBy: Option[SearchAccountDto],
  taskStatus: FindTaskStatusResp,
  priority: TaskPriority,
  createdAt: Instant,
  updatedAt: Instant,
  latestVersion: UUID,
  logEntries: Seq[LogEntryDto],
  links: Seq[FindLinkResp],
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None,
  taskLabels: Seq[FindTaskLabelResp] = Seq.empty
)

case class TaskVersionDto(
  version: UUID,
  taskId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  closedBy: Option[UUID],
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant,
  priority: TaskPriority = TaskPriority.Medium,
  milestone: Option[UUID] = None,
  startDate: LocalDate,
  dueDate: LocalDate,
  endDate: Option[LocalDate] = None
)

case class CreateLinkReq(name: String, url: String)
case class UpdateLinkReq(id: UUID, name: String, url: String)

case class CreateTaskAndLinksReq(task: CreateTaskReq, links: Seq[CreateLinkReq])

case class UpdateTaskReq(task: UpdateTaskReq1, linksForUpdate: Seq[UpdateLinkReq], linksToCreate: Seq[CreateLinkReq])

case class FindLinkResp(
  id: UUID,
  taskId: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  name: String,
  url: String,
  updatedAt: Instant
)
case class LinkVersionDto(
  version: UUID,
  linkId: UUID,
  name: String,
  url: String,
  createdAt: Instant,
  updatedAt: Instant
)
case class FindTaskResp(task: FindTaskResp1, links: Seq[FindLinkResp], logEntries: Seq[LogEntryDto])

case class LogEntryDto(
  id: UUID,
  taskId: UUID,
  createdBy: UUID,
  createdAt: Instant,
  latestVersion: UUID,
  textInternal: String,
  textExternal: String,
  status: LogEntryStatus,
  updatedAt: Instant
)

case class LogEntryVersionDto(
  version: UUID,
  logEntryId: UUID,
  textInternal: String,
  textExternal: String,
  status: LogEntryStatus,
  createdBy: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

case class CreateTaskStatusReq(projectId: UUID, name: String, description: String)
case class CreateTaskStatusResp(id: UUID, projectId: UUID, name: String, description: String)
case class FindTaskStatusResp(id: UUID, projectId: UUID, name: String, description: String)
case class UpdateTaskStatusResp(id: UUID, projectId: UUID, name: String, description: String)

case class CreateTaskLabelReq(projectId: UUID, name: String, description: String)
case class CreateTaskLabelResp(id: UUID, projectId: UUID, name: String, description: String)
case class FindTaskLabelResp(id: UUID, projectId: UUID, name: String, description: String)
case class UpdateTaskLabelResp(id: UUID, projectId: UUID, name: String, description: String)
