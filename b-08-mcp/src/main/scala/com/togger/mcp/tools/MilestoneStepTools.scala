package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class ListMilestoneStepsInput(milestoneId: String) derives Codec, Schema

class MilestoneStepTools(client: TgBackendClient):

  private val listMilestoneSteps = readOnlyTool(
    "list_milestone_steps",
    """Lists the steps of a milestone in order (name, description, status, position, start, due and end dates and the
      |comment of the latest change). status: Planned, InProgress, Done, Paused or Abandoned; a step is closed when it is
      |Done or Abandoned. A milestone can be Done while steps are still open: it was closed before its plan was carried
      |out. milestoneId is a UUID from list_milestones. A milestone has a short list of steps, so the list is not paged.""".stripMargin
  ).input[ListMilestoneStepsInput]
    .handle(input =>
      respond(
        uuid("milestoneId", input.milestoneId).flatMap(id => client.get(List("milestone-step", "milestone", id)))
      )
    )

  def all: List[McpTool] = List(listMilestoneSteps)
