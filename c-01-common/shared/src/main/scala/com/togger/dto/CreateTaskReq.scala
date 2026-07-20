package com.togger.dto

import com.togger.logentry.LogEntryStatus

import java.time.Instant
import java.util.UUID

case class CreateTaskReq(
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: Long,
  createdAt: Instant,
  updatedAt: Instant
)

case class UpdateTaskReq1(
  id: Long,
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: Long,
  createdAt: Instant,
  updatedAt: Instant
)

case class FindTaskResp1(
  id: Long,
  projectId: Long,
  name: String,
  description: String,
  assignedTo: Option[UUID],
  createdBy: UUID,
  taskStatusId: Long,
  createdAt: Instant,
  updatedAt: Instant
)

case class FindTaskRespFull(
  id: Long,
  projectId: Long,
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
case class UpdateLinkReq(id: Long, name: String, url: String)

case class CreateTaskAndLinksReq(task: CreateTaskReq, links: Seq[CreateLinkReq])

case class UpdateTaskReq(task: UpdateTaskReq1, linksForUpdate: Seq[UpdateLinkReq], linksToCreate: Seq[CreateLinkReq])

case class FindLinkResp(id: Long, name: String, url: String)
case class FindTaskResp(task: FindTaskResp1, links: Seq[FindLinkResp], logEntries: Seq[LogEntryDto])

case class LogEntryDto(
  id: Long,
  textInternal: String,
  textExternal: String,
  status: LogEntryStatus,
  taskId: Long,
  createdBy: UUID,
  createdAt: Instant,
  updatedAt: Instant
)

case class CreateTaskStatusReq(projectId: Long, name: String, description: String)
case class CreateTaskStatusResp(id: Long, projectId: Long, name: String, description: String)
case class FindTaskStatusResp(id: Long, projectId: Long, name: String, description: String)
case class UpdateTaskStatusResp(id: Long, projectId: Long, name: String, description: String)
