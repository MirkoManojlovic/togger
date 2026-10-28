package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class ListMilestonesInput(
  projectId: String,
  statuses: Option[List[Int]],
  search: Option[String],
  page: Option[Int],
  size: Option[Int]
) derives Codec,
      Schema
case class MilestoneIdInput(milestoneId: String) derives Codec, Schema

class MilestoneTools(client: TgBackendClient):

  private val listMilestones = readOnlyTool(
    "list_milestones",
    """Lists the milestones of a project (name, description, status, start, due and end dates).
      |statuses filters by status: 0 InProgress, 1 Done, 2 Paused, 3 Abandoned. search is a case-insensitive text match.
      |Paged: page starts at 1, size is 1..100, default 20.""".stripMargin
  ).input[ListMilestonesInput]
    .handle(input =>
      respond(
        uuid("projectId", input.projectId).flatMap(id =>
          client.get(
            List("milestone", "project", id),
            repeated("status", input.statuses.getOrElse(Nil).map(_.toString)) ++
              input.search.map(_.trim).filter(_.nonEmpty).map("search" -> _).toList ++
              paging(input.page, input.size)
          )
        )
      )
    )

  private val getMilestone = readOnlyTool(
    "get_milestone",
    "Gets one milestone. milestoneId is a UUID from list_milestones."
  ).input[MilestoneIdInput]
    .handle(input => respond(uuid("milestoneId", input.milestoneId).flatMap(id => client.get(List("milestone", id)))))

  def all: List[McpTool] = List(listMilestones, getMilestone)
