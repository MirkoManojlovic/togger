package com.togger.ui.accountmanagement

import cats.effect.IO
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.{Forbidden, PageRequest, PaginatedResponse}
import com.togger.dto.{AccountManagementDto, AccountProjectDto, AccountTypeDto, UpdateAccountReq}
import com.togger.preview._

import java.util.UUID

class AccountManagementFeServicePreview(
  ctx: PreviewContext,
  accounts: AccountsPreviewData,
  organizations: OrganizationsPreviewData,
  projects: ProjectsPreviewData
) extends AccountManagementFeService {

  override def findAll(page: PageRequest): TgRespIO[PaginatedResponse[AccountManagementDto]] =
    adminOnly(_ => PreviewResponses.paged(accounts.accounts.all.sorted(AccountsPreviewData.byName).map(toDto), page))

  override def findById(id: UUID): TgRespIO[AccountManagementDto] =
    adminOnly(_ =>
      accounts.accounts.find(id).fold(PreviewResponses.badRequest[AccountManagementDto]("Account not found"))(a =>
        PreviewResponses.ok(toDto(a))
      )
    )

  override def findAccountTypes: TgRespIO[List[AccountTypeDto]] =
    adminOnly(_ => PreviewResponses.ok(PreviewAccountTypes.all.sortBy(_.name)))

  override def update(req: UpdateAccountReq): TgRespIO[AccountManagementDto] =
    adminOnly(_ => IO.defer(updateAccount(req)))

  override def deleteById(id: UUID): TgRespIO[TgEmptyBody] =
    adminOnly(callerId => IO.defer(deleteAccount(callerId, id)))

  private def updateAccount(req: UpdateAccountReq): TgRespIO[AccountManagementDto] = {
    val email = req.email.trim
    accounts.accounts.find(req.id) match {
      case None => PreviewResponses.badRequest("Account not found")
      case Some(_) if !PreviewAccountTypes.all.exists(_.id == req.accountTypeId) =>
        PreviewResponses.badRequest("Unknown account type")
      case Some(_) if accounts.byEmail(email).exists(_.id != req.id) =>
        PreviewResponses.badRequest("Email is already in use by another account")
      case Some(account) =>
        val updated = account.copy(
          firstName = req.firstName.trim,
          lastName = req.lastName.trim,
          email = email,
          accountTypeId = req.accountTypeId
        )
        accounts.accounts.upsert(updated)
        PreviewResponses.ok(toDto(updated))
    }
  }

  private def deleteAccount(callerId: UUID, id: UUID): TgRespIO[TgEmptyBody] =
    if (callerId == id) PreviewResponses.badRequest("You cannot delete your own account")
    else if (!accounts.accounts.delete(id)) PreviewResponses.badRequest("Account not found")
    else {
      organizations.members.deleteWhere(_.accountId == id)
      projects.members.deleteWhere(_.accountId == id)
      PreviewResponses.okEmpty("Account deleted successfully")
    }

  private def adminOnly[A](f: UUID => TgRespIO[A]): TgRespIO[A] =
    PreviewResponses.authenticated(ctx.loginRepo) { callerId =>
      if (accounts.isAdmin(callerId)) f(callerId) else IO.pure(Left(Forbidden))
    }

  private def toDto(account: PreviewAccount): AccountManagementDto =
    AccountManagementDto(
      id = account.id,
      firstName = account.firstName,
      lastName = account.lastName,
      email = account.email,
      accountTypeId = account.accountTypeId,
      accountTypeName = PreviewAccountTypes.all.find(_.id == account.accountTypeId).fold("")(_.name),
      projects = projectsOf(account.id)
    )

  private def projectsOf(accountId: UUID): List[AccountProjectDto] =
    projects.projects
      .filter(project => projects.isMember(project.id, accountId))
      .sortBy(project => (project.name, project.id.toString))
      .map(project => AccountProjectDto(project.id, project.organizationId, project.name))
}
