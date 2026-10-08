package com.togger.maintenance

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.togger.account.{Account, AccountType}
import com.togger.common.{DbPageRequest, TgIds}
import com.togger.db.TgPages
import com.togger.organization.Organization
import com.togger.project.Project
import doobie.ConnectionIO
import doobie.implicits._

import java.util.UUID

object CreateGammaProjectApp extends IOApp with SeedAppModule {

  private val ORGANIZATION_NAME = "Maths"

  private def ensureAccountType(name: String): ConnectionIO[AccountType] =
    accountTypeRepo.findByName(name).attempt.flatMap {
      case Right(accountType) => accountType.pure[ConnectionIO]
      case Left(_)            => accountTypeRepo.insert(AccountType(id = UUID.randomUUID(), name = name, createdAt = now, updatedAt = now))
    }

  private def createOrganizationWithNewOwner: IO[(Organization, Account)] =
    for {
      accountType <- ensureAccountType("developer").transact(xa)
      ownerAccount <- accountService.createAccount(createAccount("Maths", "Owner", "mathsowner@email.com", accountType.id))
      savedOrgResult <- organizationService.saveOrganization(
        Organization(
          id = TgIds.UNASSIGNED_UUID,
          name = ORGANIZATION_NAME,
          description = "",
          icon = "🏢",
          createdAt = now,
          updatedAt = now
        ),
        ownerAccount.id,
        Seq.empty
      )
    } yield (savedOrgResult._1, ownerAccount)

  private def findExistingOrganizationOwner(organization: Organization): IO[(Organization, Account)] =
    organizationAccountRepo
      .findPaginatedMembers(organization.id, DbPageRequest.default)
      .transact(xa)
      .map { membersPage =>
        val owner = membersPage.items.headOption
          .getOrElse(throw new RuntimeException(s"""Organization "$ORGANIZATION_NAME" (${organization.id}) has no members"""))
        (organization, owner)
      }

  override def run(args: List[String]): IO[ExitCode] =
    for {
      organizations <- TgPages.readAll(organizationRepo.findPaginated).transact(xa)
      existingOrganization = organizations.find(_.name == ORGANIZATION_NAME)

      organizationAndOwner <- existingOrganization match {
        case Some(organization) => findExistingOrganizationOwner(organization)
        case None                => createOrganizationWithNewOwner
      }
      (organization, owner) = organizationAndOwner

      _ <- projectService.saveProject(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = organization.id,
          name = "Gamma",
          description = "",
          icon = "📁",
          createdAt = now,
          updatedAt = now
        ),
        owner.id,
        Seq.empty
      )
    } yield ExitCode.Success

}
