package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class TaskIdInput(taskId: String) derives Codec, Schema
case class ListTasksInput(
  projectId: String,
  statusIds: Option[List[String]],
  priorities: Option[List[Int]],
  labelIds: Option[List[String]],
  assigneeIds: Option[List[String]],
  milestoneIds: Option[List[String]],
  search: Option[String],
  timeboxId: Option[String],
  includeArchived: Option[Boolean],
  page: Option[Int],
  size: Option[Int]
) derives Codec,
      Schema

class TaskTools(client: TgBackendClient):

  private val listTaskStatuses = readOnlyTool(
    "list_task_statuses",
    "Lists the task statuses of a project (id, name, description, emoji). Paged: page starts at 1, size is 1..100, default 20."
  ).input[ProjectIdInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId)
          .flatMap(id => client.get(List("task-status", "project", id), paging(input.page, input.size)))
      )
    )

  private val listTaskLabels = readOnlyTool(
    "list_task_labels",
    "Lists the task labels of a project (id, name, description, emoji). Paged: page starts at 1, size is 1..100, default 20."
  ).input[ProjectIdInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId)
          .flatMap(id => client.get(List("task-label", "project", id), paging(input.page, input.size)))
      )
    )

  private val listTasks = readOnlyTool(
    "list_tasks",
    """Lists the tasks of a project with assignee, status, priority, dates, labels, links and log entries.
      |Filters are optional and combined with AND; several values inside one filter are combined with OR.
      |statusIds: UUIDs from list_task_statuses. priorities: 1 Critical, 2 High, 3 Medium, 4 Low, 5 Planned.
      |labelIds: UUIDs from list_task_labels (task has at least one). assigneeIds: UUIDs from search_accounts,
      |or 00000000-0000-0000-0000-000000000000 for unassigned tasks. milestoneIds: UUIDs from list_milestones.
      |search: case-insensitive text in task name or description. timeboxId: only tasks planned in that timebox.
      |includeArchived defaults to false. Paged: page starts at 1, size is 1..100, default 20.""".stripMargin
  ).input[ListTasksInput]
    .handle(input =>
      respond(
        for
          projectId <- uuid("projectId", input.projectId)
          statusIds <- uuids("statusIds", input.statusIds)
          labelIds <- uuids("labelIds", input.labelIds)
          assigneeIds <- uuids("assigneeIds", input.assigneeIds)
          milestoneIds <- uuids("milestoneIds", input.milestoneIds)
          timeboxId <- input.timeboxId.filter(_.nonEmpty).fold(Right(None))(uuid("timeboxId", _).map(Some(_)))
          tasks <- client.get(
            List("task", "project-id", projectId),
            repeated("statusId", statusIds) ++
              repeated("priority", input.priorities.getOrElse(Nil).map(_.toString)) ++
              repeated("labelId", labelIds) ++
              repeated("assigneeId", assigneeIds) ++
              repeated("milestoneId", milestoneIds) ++
              input.search.map(_.trim).filter(_.nonEmpty).map("search" -> _).toList ++
              timeboxId.map("timeboxId" -> _).toList ++
              input.includeArchived.map("includeArchived" -> _.toString).toList ++
              paging(input.page, input.size)
          )
        yield tasks
      )
    )

  private val getTask = readOnlyTool(
    "get_task",
    "Gets one task with its links and log entries. taskId is a UUID from list_tasks."
  ).input[TaskIdInput]
    .handle(input => respond(uuid("taskId", input.taskId).flatMap(id => client.get(List("task", id)))))

  def all: List[McpTool] = List(listTaskStatuses, listTaskLabels, listTasks, getTask)
