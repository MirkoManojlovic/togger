package com.togger.mcp.tools

import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class ListMilestoneStepsInput(milestoneId: String) derives Codec, Schema

class MilestoneStepTools(client: TgBackendClient):

  private val listMilestoneSteps = readOnlyTool(
    "list_milestone_steps",
    """Lists the steps of a milestone in order (name, description, status, position, start, due and end dates and the
      |comment of the latest change, and tasksTotal / tasksDone: the tasks in the step; a task is done when it has an
      |end date). Tasks belong to a step; filter list_tasks with milestoneStepIds. status: Planned, InProgress, Done, Paused or Abandoned; a step is closed when it is
      |Done or Abandoned. A milestone can be Done while steps are still open: it was closed before its plan was carried
      |out. position is the stage of the step: steps with the same position run in parallel, stages run in sequence and a
      |gap between positions is allowed. milestoneId is a UUID from list_milestones. A milestone has a short list of steps, so the list is not paged.""".stripMargin
  ).input[ListMilestoneStepsInput]
    .handle(input =>
      respond(
        uuid("milestoneId", input.milestoneId).flatMap(id => client.get(List("milestone-step", "milestone", id)))
      )
    )

  def all: List[McpTool] = List(listMilestoneSteps)
