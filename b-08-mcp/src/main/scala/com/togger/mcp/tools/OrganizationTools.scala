package com.togger.mcp.tools

import chimp.server.*
import com.togger.mcp.TgBackendClient
import com.togger.mcp.ToolSupport.*
import io.circe.Codec
import sttp.tapir.Schema

case class PageInput(page: Option[Int], size: Option[Int]) derives Codec, Schema
case class OrganizationIdInput(organizationId: String, page: Option[Int], size: Option[Int]) derives Codec, Schema

class OrganizationTools(client: TgBackendClient):

  private val listOrganizations = readOnlyTool(
    "list_organizations",
    "Lists all organizations (members are not included, use get_organization for those). Paged: page starts at 1, size is 1..100, default 20."
  ).input[PageInput].handle(input => respond(client.get(List("organization"), paging(input.page, input.size))))

  private val getOrganization = readOnlyTool(
    "get_organization",
    "Gets one organization with its name, description, icon and members. organizationId is a UUID from list_organizations."
  ).input[OrganizationIdInput]
    .handle(input =>
      respond(uuid("organizationId", input.organizationId).flatMap(id => client.get(List("organization", id))))
    )

  private val listOrganizationMembers = readOnlyTool(
    "list_organization_members",
    "Lists the accounts that are members of an organization. Paged: page starts at 1, size is 1..100, default 20."
  ).input[OrganizationIdInput]
    .handle(input =>
      respond(
        uuid("organizationId", input.organizationId)
          .flatMap(id => client.get(List("organization", "members", id), paging(input.page, input.size)))
      )
    )

  def all: List[McpTool] = List(listOrganizations, getOrganization, listOrganizationMembers)
