package com.togger

import java.time.Instant
import java.util.UUID

import cats.effect.IO
import com.togger.account._
import com.togger.common.{Password, TgClockImpl, TgIds}
import com.togger.logging.TgLogging
import com.togger.organization.OrganizationAccountRepoImpl.ORGANIZATION_ACCOUNT_TABLE_FRAG
import com.togger.organization.OrganizationRepoImpl.ORGANIZATION_TABLE_FRAG
import com.togger.project.ProjectAccountRepoImpl.PROJECT_ACCOUNT_TABLE_FRAG
import com.togger.project.ProjectRepoImpl.PROJECT_TABLE_FRAG
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Transactor}

/** Makes sure the account used by the MCP server exists, is an admin and is a member of every organization and project.
  * Runs on every start and only adds what is missing.
  */
class McpAccountSeedService(transactor: Transactor[IO]) extends TgLogging {
  private val accountTypeRepo = new AccountTypeRepo
  private val accountRepo = new AccountRepoImpl
  private val tgClock = new TgClockImpl

  def run: IO[Unit] =
    for {
      now <- IO(tgClock.currentInstant)
      accountId <- (for {
        adminType <- findOrCreateAdminType(now)
        account <- findOrCreateAccount(adminType.id, now)
      } yield account.id).transact(transactor)
      _ <- addToAllOrganizations(accountId, now).transact(transactor)
      _ <- addToAllProjects(accountId, now).transact(transactor)
    } yield ()

  private def findOrCreateAdminType(now: Instant): ConnectionIO[AccountType] =
    accountTypeExists(AdminCheck.AdminAccountTypeName).flatMap {
      case true => accountTypeRepo.findByName(AdminCheck.AdminAccountTypeName)
      case false =>
        accountTypeRepo.insert(
          AccountType(
            id = TgIds.UNASSIGNED_UUID,
            name = AdminCheck.AdminAccountTypeName,
            createdAt = now,
            updatedAt = now
          )
        )
    }

  private def findOrCreateAccount(adminTypeId: UUID, now: Instant): ConnectionIO[Account] =
    accountExists(McpAccountSeedService.Email).flatMap {
      case true  => accountRepo.findByEmail(McpAccountSeedService.Email)
      case false => accountRepo.insert(newAccount(adminTypeId, now))
    }

  private def newAccount(adminTypeId: UUID, now: Instant): Account =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = McpAccountSeedService.FirstName,
      lastName = McpAccountSeedService.LastName,
      email = McpAccountSeedService.Email,
      password = HashedPasswordImpl.fromPassword(Password.fromString(McpAccountSeedService.PlainPassword).toOption.get),
      accountTypeId = adminTypeId,
      verified = true,
      enabled = true,
      createdAt = now,
      updatedAt = now
    )

  private def accountTypeExists(name: String): ConnectionIO[Boolean] =
    sql"select exists (select 1 from account_type where name = $name)".query[Boolean].unique

  private def accountExists(email: String): ConnectionIO[Boolean] =
    sql"select exists (select 1 from account where email = $email)".query[Boolean].unique

  private def addToAllOrganizations(accountId: UUID, now: Instant): ConnectionIO[Int] =
    (fr"insert into" ++ ORGANIZATION_ACCOUNT_TABLE_FRAG ++ fr"(organization_id, account_id, created_at)" ++
      fr"select organization.id, $accountId, $now from" ++ ORGANIZATION_TABLE_FRAG ++
      fr"""where not exists (
        select 1 from organization_account
        where organization_account.organization_id = organization.id and organization_account.account_id = $accountId)""").update.run

  private def addToAllProjects(accountId: UUID, now: Instant): ConnectionIO[Int] =
    (fr"insert into" ++ PROJECT_ACCOUNT_TABLE_FRAG ++ fr"(project_id, account_id, created_at)" ++
      fr"select project.id, $accountId, $now from" ++ PROJECT_TABLE_FRAG ++
      fr"""where not exists (
        select 1 from project_account
        where project_account.project_id = project.id and project_account.account_id = $accountId)""").update.run
}

object McpAccountSeedService {
  val FirstName: String = "mcp"
  val LastName: String = "mcp"
  val Email: String = "mcp@email.com"
  val PlainPassword: String = "A!good_1_password_is_good"
}
