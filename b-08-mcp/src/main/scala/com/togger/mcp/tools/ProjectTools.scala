package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.{Codec, Json}
import sttp.tapir.Schema

case class ProjectIdInput(projectId: String, page: Option[Int], size: Option[Int]) derives Codec, Schema
case class ProjectIdOnlyInput(projectId: String) derives Codec, Schema
case class ListProjectsInput(organizationId: String, page: Option[Int], size: Option[Int]) derives Codec, Schema
case class SearchProjectsInput(projectLabelIds: List[String], page: Option[Int], size: Option[Int])
    derives Codec,
      Schema

class ProjectTools(client: TgBackendClient):
  private val MaxPageSize = "100"

  private val listProjects = readOnlyTool(
    "list_projects",
    "Lists the projects of an organization that the mcp account is a member of. Each project has its name, description, icon, work days, members and labels. Paged: page starts at 1, size is 1..100, default 20."
  ).input[ListProjectsInput]
    .handle(input =>
      respond(
        uuid("organizationId", input.organizationId)
          .flatMap(id => client.get(List("project", "organization", id), paging(input.page, input.size)))
      )
    )

  private val searchProjectsByLabels = readOnlyTool(
    "search_projects_by_labels",
    "Finds projects (across organizations) that have ALL the given project labels. projectLabelIds are UUIDs from list_project_labels. Paged like list_projects."
  ).input[SearchProjectsInput]
    .handle(input =>
      respond(
        uuids("projectLabelIds", Some(input.projectLabelIds)).flatMap(ids =>
          client.post(
            List("project", "search"),
            paging(input.page, input.size),
            Json.obj("projectLabelIds" -> Json.fromValues(ids.map(Json.fromString)))
          )
        )
      )
    )

  private val getProject = readOnlyTool(
    "get_project",
    "Gets one project: name, description, icon, work days (ISO 1=Mon..7=Sun), members and project labels. projectId is a UUID from list_projects."
  ).input[ProjectIdOnlyInput]
    .handle(input => respond(uuid("projectId", input.projectId).flatMap(id => client.get(List("project", id)))))

  private val listProjectMembers = readOnlyTool(
    "list_project_members",
    "Lists the accounts that are members of a project. Paged: page starts at 1, size is 1..100, default 20."
  ).input[ProjectIdInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId)
          .flatMap(id => client.get(List("project", "members", id), paging(input.page, input.size)))
      )
    )

  private val getProjectOverview = readOnlyTool(
    "get_project_overview",
    "Everything needed to understand a project in one call: the project (description, members, project labels), its task statuses and its task labels. Use the status and label ids it returns to filter list_tasks."
  ).input[ProjectIdOnlyInput]
    .handle(input =>
      respond(
        for
          id <- uuid("projectId", input.projectId)
          project <- client.get(List("project", id))
          statuses <- client.get(List("task-status", "project", id), List("size" -> MaxPageSize))
          labels <- client.get(List("task-label", "project", id), List("size" -> MaxPageSize))
        yield Json.obj("project" -> project, "taskStatuses" -> statuses, "taskLabels" -> labels)
      )
    )

  private val listProjectLabels = readOnlyTool(
    "list_project_labels",
    "Lists the project labels (languages, frameworks, infrastructure, domains) that can be attached to projects. Paged: page starts at 1, size is 1..100, default 20."
  ).input[PageInput].handle(input => respond(client.get(List("project-label"), paging(input.page, input.size))))

  def all: List[McpTool] =
    List(listProjects, searchProjectsByLabels, getProject, listProjectMembers, getProjectOverview, listProjectLabels)
