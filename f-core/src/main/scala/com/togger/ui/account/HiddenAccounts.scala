package com.togger.ui.account

import com.togger.common.PaginatedResponse
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.organization.FindOrganizationResp
import com.togger.dto.{FindProjectResp, SearchAccountDto}

/** Accounts that exist for tooling (the MCP server) and are never shown as members. */
object HiddenAccounts {
  val McpEmail: String = "mcp@email.com"

  private def isVisible(account: SearchAccountDto): Boolean =
    !account.email.equalsIgnoreCase(McpEmail)

  def visible(accounts: Seq[SearchAccountDto]): Seq[SearchAccountDto] =
    accounts.filter(isVisible)

  def visiblePage(resp: TgRespIO[PaginatedResponse[SearchAccountDto]]): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    resp.map(_.map(success => success.copy(body = success.body.map(page => page.copy(items = page.items.filter(isVisible))))))

  def visibleProject(project: FindProjectResp): FindProjectResp =
    project.copy(members = visible(project.members))

  def visibleProjects(resp: TgRespIO[PaginatedResponse[FindProjectResp]]): TgRespIO[PaginatedResponse[FindProjectResp]] =
    resp.map(_.map(success => success.copy(body = success.body.map(page => page.copy(items = page.items.map(visibleProject))))))

  def visibleProjectResp(resp: TgRespIO[FindProjectResp]): TgRespIO[FindProjectResp] =
    resp.map(_.map(success => success.copy(body = success.body.map(visibleProject))))

  def visibleOrganization(resp: TgRespIO[FindOrganizationResp]): TgRespIO[FindOrganizationResp] =
    resp.map(_.map(success => success.copy(body = success.body.map(org => org.copy(members = visible(org.members))))))

  def visibleOrganizations(
    resp: TgRespIO[PaginatedResponse[FindOrganizationResp]]
  ): TgRespIO[PaginatedResponse[FindOrganizationResp]] =
    resp.map(
      _.map(success =>
        success.copy(body =
          success.body.map(page => page.copy(items = page.items.map(org => org.copy(members = visible(org.members)))))
        )
      )
    )
}
