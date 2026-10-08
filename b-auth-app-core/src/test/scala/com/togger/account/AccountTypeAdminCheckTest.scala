package com.togger.account

import com.togger.common.{TgIds, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

import java.util.UUID

class AccountTypeAdminCheckTest extends AccountTgDbSpec with TimeHelpers with TgAccount {

  private val accountRepo = new AccountRepoImpl

  private def accountOfType(typeName: String, email: String) =
    for {
      accountType <- accountTypeRepo.insert(AccountType(TgIds.UNASSIGNED_UUID, typeName, nowAsInstant, nowAsInstant))
      account <- accountRepo.insert(createAccount(accountType.id).copy(email = email))
    } yield account

  testNameFromClassName should {

    "tell an account of the admin type from one of any other type" in { transactor =>
      val adminCheck = new AccountTypeAdminCheck(accountRepo, accountTypeRepo, transactor)
      for {
        accounts <- (for {
          admin <- accountOfType(AdminCheck.AdminAccountTypeName, "admin@email.com")
          manager <- accountOfType("manager", "manager@email.com")
        } yield (admin, manager)).transact(transactor)
        (admin, manager) = accounts
        adminResult <- adminCheck.isAdmin(admin.id)
        managerResult <- adminCheck.isAdmin(manager.id)
        unknownResult <- adminCheck.isAdmin(UUID.randomUUID())
      } yield {
        adminResult shouldBe true
        managerResult shouldBe false
        unknownResult shouldBe false
      }
    }
  }
}
