package com.togger.controllers.organization

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.common.{DbPageRequest, PaginatedResponse, TgClock}
import com.togger.logging.TgLogging
import com.togger.organization.{Organization, OrganizationAccount, OrganizationAccountRepo, OrganizationRepo}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

import java.util.UUID

class OrganizationService(
  organizationRepo: OrganizationRepo,
  organizationAccountRepo: OrganizationAccountRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def saveOrganization(
    organization: Organization,
    accountId: UUID,
    membersToAdd: Seq[UUID]
  ): IO[(Organization, Seq[Account])] = {
    (for {
      savedOrg <- organizationRepo.insert(organization)
      _ <- organizationAccountRepo.insert(
        OrganizationAccount(
          organizationId = savedOrg.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      _ <- addMembers(savedOrg, membersToAdd)
      members <- findMembersLocal(savedOrg.id)
    } yield savedOrg -> members).transact(transactor)
  }

  private def addMembers(
    organization: Organization,
    membersToAdd: Seq[UUID]
  ): ConnectionIO[List[OrganizationAccount]] = {
    membersToAdd
      .map(accountId =>
        OrganizationAccount(
          organizationId = organization.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      .map(organizationAccountRepo.insert)
      .toList
      .sequence
  }

  private def removeMembers(organization: Organization, membersToRemove: Seq[UUID]): ConnectionIO[List[Int]] = {
    membersToRemove
      .map(accountId =>
        OrganizationAccount(
          organizationId = organization.id,
          accountId = accountId,
          createdAt = tgClock.currentInstant
        )
      )
      .map(account =>
        organizationAccountRepo.deleteById(organizationId = organization.id, accountId = account.accountId)
      )
      .toList
      .sequence
  }

  // The members embedded in an organization response are the first page of the maximum size; an organization with more
  // members shows only those (the paginated members endpoint reads them all).
  private def findMembersLocal(organizationId: UUID): ConnectionIO[Seq[Account]] =
    organizationAccountRepo.findPaginatedMembers(organizationId, DbPageRequest.firstMaxSized).map(_.items)

  def findBy(id: UUID): IO[(Organization, Seq[Account])] = {
    (for {
      organization <- organizationRepo.findById(id)
      members <- findMembersLocal(id)
    } yield organization -> members).transact(transactor)
  }

  def updateOrganization(
    organization: Organization,
    membersToAdd: Seq[UUID],
    membersToRemove: Seq[UUID]
  ): IO[(Organization, Seq[Account])] = {
    logger.info(s"Updating a ${sp(organization)}")
    val now = tgClock.currentInstant
    (for {
      updatedOrganization <- organizationRepo.update(organization, now)
      _ <- addMembers(updatedOrganization, membersToAdd)
      _ <- removeMembers(updatedOrganization, membersToRemove)
      members <- findMembersLocal(updatedOrganization.id)
    } yield {
      logger.info(s"Organization updated successfully ${sp(updatedOrganization)}")
      updatedOrganization -> members
    }).transact(transactor)
  }

  def deleteById(id: UUID): IO[Int] = {
    organizationRepo.deleteById(id).transact(transactor)
  }

  /** Every organization of every tenant: only for admins. */
  def findPaginated(page: DbPageRequest): IO[PaginatedResponse[Organization]] =
    organizationRepo.findPaginated(page).transact(transactor)

  def findPaginatedByAccountId(accountId: UUID, page: DbPageRequest): IO[PaginatedResponse[Organization]] =
    organizationRepo.findPaginatedByAccountId(accountId, page).transact(transactor)

  def findPaginatedMembers(organizationId: UUID, page: DbPageRequest): IO[PaginatedResponse[Account]] =
    organizationAccountRepo.findPaginatedMembers(organizationId, page).transact(transactor)
}
