package com.togger.controllers.organization

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account.Account
import com.togger.common.TgClock
import com.togger.logging.TgLogging
import com.togger.organization.{Organization, OrganizationAccount, OrganizationAccountRepo, OrganizationRepo}
import com.togger.project.{Project, ProjectRepo}
import com.togger.util.SplunkMacro.sp
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

class OrganizationService(
  organizationRepo: OrganizationRepo,
  organizationAccountRepo: OrganizationAccountRepo,
  projectRepo: ProjectRepo,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def saveOrganization(
    organization: Organization,
    accountId: Long,
    membersToAdd: Seq[Long]
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
    membersToAdd: Seq[Long]
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

  private def removeMembers(organization: Organization, membersToRemove: Seq[Long]): ConnectionIO[List[Int]] = {
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

  private def findMembersLocal(organizationId: Long): ConnectionIO[Seq[Account]] = {
    organizationAccountRepo.findAllMembers(organizationId)
  }

  def findBy(id: Long): IO[(Organization, Seq[Account])] = {
    (for {
      organization <- organizationRepo.findById(id)
      members <- organizationAccountRepo.findAllMembers(id)
    } yield organization -> members).transact(transactor)
  }

  def updateOrganization(
    organization: Organization,
    membersToAdd: Seq[Long],
    membersToRemove: Seq[Long]
  ): IO[(Organization, Seq[Account])] = {
    logger.info(s"Updating a ${sp(organization)}")
    val now = tgClock.currentInstant
    (for {
      updatedOrganization <- organizationRepo.update(organization, now)
      _ <- addMembers(updatedOrganization, membersToAdd)
      _ <- removeMembers(updatedOrganization, membersToRemove)
      members <- organizationAccountRepo.findAllMembers(updatedOrganization.id)
    } yield {
      logger.info(s"Organization updated successfully ${sp(updatedOrganization)}")
      updatedOrganization -> members
    }).transact(transactor)
  }

  def deleteById(id: Long): IO[Int] = {
    organizationRepo.deleteById(id).transact(transactor)
  }

  def findAll(): IO[Seq[Organization]] = {
    organizationRepo.findAll().transact(transactor)
  }

  def findAllOrgByAccountId(accountId: Long): IO[Seq[Organization]] =
    organizationRepo.findAllOrgByAccountId(accountId).map(_.map(_._1)).transact(transactor)

  def findAllMembers(organizationId: Long): IO[Seq[Account]] =
    organizationAccountRepo.findAllMembers(organizationId).transact(transactor)

  def findOrganizationsAndProjectsByAccountId(accountId: Long): IO[List[(Organization, Seq[Project])]] = {
    (for {
      organizations <- organizationRepo.findAllOrgByAccountId(accountId).map(_.map(_._1))
      orgsAndProjects <- organizations
        .map { organization =>
          projectRepo
            .findByOrganizationIdAndAccountId(organization.id, accountId)
            .map(_.map(_._1))
            .map(projects => organization -> projects)
        }
        .toList
        .sequence
    } yield (orgsAndProjects)).transact(transactor)
  }
}
