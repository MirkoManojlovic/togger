package com.togger.organization

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, TgIds, TimeHelpers}
import doobie.implicits._
import doobie.postgres.implicits._

class OrganizationAccountRepoTest extends BusinessTgDbSpec with TgAccount with TgOrganization with TimeHelpers {

  val organizationAccountRepo = new OrganizationAccountRepoImpl
  val accountRepo = new AccountRepoImpl

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedOrganization <- createOrganization
        savedOrgAcc <- organizationAccountRepo.insert(
          OrganizationAccount(
            organizationId = savedOrganization.id,
            accountId = savedAccount.id,
            createdAt = nowAsInstant
          )
        )
        foundOrgAcc <- organizationAccountRepo.findById(
          organizationId = savedOrgAcc.organizationId,
          accountId = savedOrgAcc.accountId
        )
        _ <- organizationAccountRepo.deleteById(
          organizationId = foundOrgAcc.organizationId,
          accountId = foundOrgAcc.accountId
        )
        foundOrgAcc2 <- organizationAccountRepo
          .findById(
            organizationId = savedOrgAcc.organizationId,
            accountId = savedOrgAcc.accountId
          )
          .attempt
      } yield {
        val cp = new Checkpoint
        cp(savedOrgAcc shouldBe foundOrgAcc)
        cp.reportAll()
        foundOrgAcc2.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }

  "find all members of the org" in { transactor =>
    val accounts = Seq(
      createAccount("John", "Smith", "shark@email.com"),
      createAccount("Jack", "Curry", "bird@email.com"),
      createAccount("Steve", "Murry", "bird1@email.com"),
      createAccount("George", "Washington", "dog@email.com")
    )

    val res = for {
      accountTypes <- createAccountTypes
      savedAccounts <- accounts
        .map(account => accountRepo.insert(account.copy(accountTypeId = accountTypes.head.id)))
        .sequence
      savedOrganization <- createOrganization
      orgAccountsForSaving = savedAccounts.map(account =>
        OrganizationAccount(
          organizationId = savedOrganization.id,
          accountId = account.id,
          createdAt = nowAsInstant
        )
      )
      _ <- orgAccountsForSaving.map(organizationAccountRepo.insert).sequence
      foundAccounts <- organizationAccountRepo.findAllMembers(savedOrganization.id)
    } yield {
      savedAccounts shouldBe foundAccounts
    }

    res.transact(transactor)
  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = TgIds.UNASSIGNED_ACCOUNT_ID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = passwordHashed,
      accountTypeId = TgIds.UNASSIGNED_ACCOUNT_ID,
      verified = true,
      enabled = true,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )
}
