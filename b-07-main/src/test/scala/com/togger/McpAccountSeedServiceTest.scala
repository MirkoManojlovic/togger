package com.togger

import java.time.Instant

import cats.implicits._
import com.togger.account._
import com.togger.common.{BusinessTgDbSpec, Password, TgIds}
import com.togger.organization.{Organization, OrganizationRepoImpl}
import com.togger.project.{Project, ProjectRepoImpl}
import doobie.implicits._
import doobie.postgres.implicits._

class McpAccountSeedServiceTest extends BusinessTgDbSpec {

  private val accountRepo = new AccountRepoImpl
  private val accountTypeRepo = new AccountTypeRepo
  private val organizationRepo = new OrganizationRepoImpl
  private val projectRepo = new ProjectRepoImpl
  private val now = Instant.parse("2026-01-01T00:00:00Z")

  private def insertOrganizationWithProject(name: String) =
    for {
      organization <- organizationRepo.insert(Organization(TgIds.UNASSIGNED_UUID, name, "", "", now, now))
      project <- projectRepo.insert(
        Project(
          id = TgIds.UNASSIGNED_UUID,
          organizationId = organization.id,
          name = name,
          description = "",
          icon = "",
          createdAt = now,
          updatedAt = now
        )
      )
    } yield (organization, project)

  testNameFromClassName should {

    "create an admin mcp account that can log in with the known password" in { transactor =>
      for {
        _ <- new McpAccountSeedService(transactor).run
        account <- accountRepo.findByEmail(McpAccountSeedService.Email).transact(transactor)
        adminType <- accountTypeRepo.findByName(AdminCheck.AdminAccountTypeName).transact(transactor)
      } yield {
        account.firstName shouldBe "mcp"
        account.lastName shouldBe "mcp"
        account.accountTypeId shouldBe adminType.id
        account.password.verify(Password.fromString(McpAccountSeedService.PlainPassword).toOption.get) shouldBe true
      }
    }

    "be idempotent" in { transactor =>
      val service = new McpAccountSeedService(transactor)
      for {
        _ <- service.run
        _ <- service.run
        accounts <- sql"select count(*) from account where email = ${McpAccountSeedService.Email}"
          .query[Int]
          .unique
          .transact(transactor)
        adminTypes <- sql"select count(*) from account_type where name = ${AdminCheck.AdminAccountTypeName}"
          .query[Int]
          .unique
          .transact(transactor)
      } yield (accounts, adminTypes) shouldBe ((1, 1))
    }

    "reuse an existing admin account type and leave an existing mcp account untouched" in { transactor =>
      for {
        adminType <- accountTypeRepo
          .insert(AccountType(TgIds.UNASSIGNED_UUID, AdminCheck.AdminAccountTypeName, now, now))
          .transact(transactor)
        existing <- accountRepo
          .insert(
            Account(
              TgIds.UNASSIGNED_UUID,
              "Someone",
              "Else",
              McpAccountSeedService.Email,
              HashedPasswordImpl.fromPassword(Password.fromString("An0ther!password_is_good").toOption.get),
              adminType.id,
              verified = true,
              enabled = true,
              now,
              now
            )
          )
          .transact(transactor)
        _ <- new McpAccountSeedService(transactor).run
        after <- accountRepo.findByEmail(McpAccountSeedService.Email).transact(transactor)
        adminTypes <- sql"select count(*) from account_type".query[Int].unique.transact(transactor)
      } yield {
        after.id shouldBe existing.id
        after.firstName shouldBe "Someone"
        adminTypes shouldBe 1
      }
    }

    "add the mcp account to every organization and project, once" in { transactor =>
      val service = new McpAccountSeedService(transactor)
      for {
        _ <- List("a", "b").traverse(insertOrganizationWithProject).transact(transactor)
        _ <- service.run
        _ <- service.run
        mcp <- accountRepo.findByEmail(McpAccountSeedService.Email).transact(transactor)
        organizations <- sql"select count(*) from organization_account where account_id = ${mcp.id}"
          .query[Int]
          .unique
          .transact(transactor)
        projects <- sql"select count(*) from project_account where account_id = ${mcp.id}"
          .query[Int]
          .unique
          .transact(transactor)
      } yield (organizations, projects) shouldBe ((2, 2))
    }

    "add the mcp account to organizations and projects created after the first run" in { transactor =>
      val service = new McpAccountSeedService(transactor)
      for {
        _ <- service.run
        _ <- insertOrganizationWithProject("late").transact(transactor)
        _ <- service.run
        projects <- sql"select count(*) from project_account".query[Int].unique.transact(transactor)
      } yield projects shouldBe 1
    }
  }
}
