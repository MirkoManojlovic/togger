package com.togger.dto

import com.togger.logentry.LogEntryStatus

import java.time.Instant
import java.util.UUID

case class CreateTaskReq(
  projectId: UUID,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: UUID,
  createdAt: Instant,
  updatedAt: Instant
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
  updatedAt: Instant
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
  updatedAt: Instant
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
  createdAt: Instant,
  updatedAt: Instant,
  logEntries: Seq[LogEntryDto],
  links: Seq[FindLinkResp]
)

case class CreateLinkReq(name: String, url: String)
case class UpdateLinkReq(id: UUID, name: String, url: String)

case class CreateTaskAndLinksReq(task: CreateTaskReq, links: Seq[CreateLinkReq])

case class UpdateTaskReq(task: UpdateTaskReq1, linksForUpdate: Seq[UpdateLinkReq], linksToCreate: Seq[CreateLinkReq])

case class FindLinkResp(id: UUID, name: String, url: String)
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
