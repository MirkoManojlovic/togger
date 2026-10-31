package com.togger.ui.organization

import cats.effect.IO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{Forbidden, PageRequest, PaginatedResponse}
import com.togger.dto.SearchAccountDto
import com.togger.dto.organization.{CreateOrganizationReq, FindOrganizationResp, UpdateOrganizationReq}
import com.togger.preview._

import java.util.UUID

class OrganizationServiceFePreview(
  ctx: PreviewContext,
  accounts: AccountsPreviewData,
  organizations: OrganizationsPreviewData,
  projects: ProjectsPreviewData
) extends OrganizationServiceFe {

  override def createOrganization(organization: CreateOrganizationReq): TgRespIO[FindOrganizationResp] =
    PreviewResponses.authenticated(ctx.loginRepo) { callerId =>
      IO.defer {
        val created = PreviewOrganization(
          UUID.randomUUID(),
          organization.name,
          organization.description,
          organization.icon
        )
        organizations.organizations.upsert(created)
        organizations.addMembers(created.id, callerId +: organization.membersToAdd)
        PreviewResponses.ok(toResp(created))
      }
    }

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[FindOrganizationResp]] =
    PreviewResponses.authenticated(ctx.loginRepo) { callerId =>
      if (accounts.isAdmin(callerId)) IO.defer(PreviewResponses.paged(sorted(organizations.organizations.all), page))
      else IO.pure(Left(Forbidden))
    }

  override def findById(id: UUID): TgRespIO[FindOrganizationResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.fromOption(organizations.organizations.find(id).map(toResp)))
    )

  override def deleteOrganization(id: UUID): TgRespIO[TgEmptyBody] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        if (organizations.organizations.find(id).isEmpty) PreviewResponses.notFound[TgEmptyBody]
        else {
          projects.projects.filter(_.organizationId == id).foreach(project => projects.deleteProject(project.id))
          organizations.members.deleteWhere(_.parentId == id)
          organizations.organizations.delete(id)
          PreviewResponses.okEmpty("Organization deleted successfully")
        }
      }
    )

  override def update(dto: UpdateOrganizationReq): TgRespIO[FindOrganizationResp] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val updated = organizations.organizations.update(dto.id)(_.copy(name = dto.name, description = dto.description, icon = dto.icon))
        updated.foreach { _ =>
          organizations.removeMembers(dto.id, dto.membersToRemove)
          organizations.addMembers(dto.id, dto.membersToAdd)
        }
        PreviewResponses.fromOption(updated.map(toResp))
      }
    )

  override def findByAccountId(
    accountId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[FindOrganizationResp]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer {
        val ids = organizations.organizationIdsOf(accountId)
        PreviewResponses.paged(sorted(organizations.organizations.filter(o => ids.contains(o.id))), page)
      }
    )

  override def findMembers(
    organizationId: UUID,
    page: PageRequest
  ): TgRespIO[PaginatedResponse[SearchAccountDto]] =
    PreviewResponses.authenticated(ctx.loginRepo)(_ =>
      IO.defer(PreviewResponses.paged(accounts.searchDtos(organizations.memberIds(organizationId)), page))
    )

  private def sorted(items: List[PreviewOrganization]): List[FindOrganizationResp] =
    items.sortBy(o => (o.name, o.id.toString)).map(toResp)

  private def toResp(organization: PreviewOrganization): FindOrganizationResp =
    FindOrganizationResp(
      organization.id,
      organization.name,
      organization.description,
      organization.icon,
      accounts.searchDtos(organizations.memberIds(organization.id))
    )
}
