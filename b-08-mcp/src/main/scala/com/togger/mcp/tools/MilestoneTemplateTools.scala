package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class ListTemplatesInput(projectId: String, page: Option[Int], size: Option[Int]) derives Codec, Schema

class MilestoneTemplateTools(client: TgBackendClient):

  private val listMilestoneTemplates = readOnlyTool(
    "list_milestone_templates",
    """Lists the milestone templates of a project, by name (name, description and the ordered steps a milestone created
      |from the template starts with; each step has the name and description of its step template).
      |Paged: page starts at 1, size is 1..100, default 20.""".stripMargin
  ).input[ListTemplatesInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId).flatMap(id =>
          client.get(List("milestone-template", "project", id), paging(input.page, input.size))
        )
      )
    )

  private val listMilestoneStepTemplates = readOnlyTool(
    "list_milestone_step_templates",
    """Lists the milestone step templates of a project, by name (name and description of a step such as Design or
      |Production). Milestone templates are made of them.
      |Paged: page starts at 1, size is 1..100, default 20.""".stripMargin
  ).input[ListTemplatesInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId).flatMap(id =>
          client.get(List("milestone-step-template", "project", id), paging(input.page, input.size))
        )
      )
    )

  def all: List[McpTool] = List(listMilestoneTemplates, listMilestoneStepTemplates)
