package com.togger.organization

import cats.implicits._
import com.togger.account.{Account, AccountRepoImpl, TgAccount}
import com.togger.common.{BusinessTgDbSpec, DbPageRequest, PageRequest, TgIds, TimeHelpers}
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
      foundAccounts <- organizationAccountRepo.findPaginatedMembers(savedOrganization.id, DbPageRequest.default)
    } yield {
      // by last name: Curry, Murry, Smith, Washington
      foundAccounts.items shouldBe savedAccounts.sortBy(_.lastName)
      foundAccounts.totalNumberOfItems shouldBe 4L
    }

    res.transact(transactor)
  }

  "split 11 members over 3 pages of 5, by last name, first name, without a duplicate" in { transactor =>
    val res = for {
      accountTypes <- createAccountTypes
      // two members share a last name, so first name decides; inserted in reverse so the order is not insertion order
      savedAccounts <- (0 to 10).toList.reverse
        .map(index =>
          createAccount(f"First $index%02d", if (index < 2) "Same" else f"Last $index%02d", s"member$index@email.com")
        )
        .map(account => accountRepo.insert(account.copy(accountTypeId = accountTypes.head.id)))
        .sequence
      savedOrganization <- createOrganization
      otherOrganization <- organizationRepo.insert(organization.copy(name = "Other organization"))
      _ <- savedAccounts.traverse(account =>
        organizationAccountRepo.insert(OrganizationAccount(savedOrganization.id, account.id, nowAsInstant))
      )
      _ <- organizationAccountRepo.insert(
        OrganizationAccount(otherOrganization.id, savedAccounts.head.id, nowAsInstant)
      )
      pages <- (1 to 3).toList.traverse(number =>
        organizationAccountRepo.findPaginatedMembers(
          savedOrganization.id,
          DbPageRequest.fromPageRequest(PageRequest(number, 5)).toOption.get
        )
      )
      past <- organizationAccountRepo.findPaginatedMembers(
        savedOrganization.id,
        DbPageRequest.fromPageRequest(PageRequest(4, 5)).toOption.get
      )
    } yield {
      val expected = savedAccounts.sortBy(account => (account.lastName, account.firstName))
      val cp = new Checkpoint
      cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
      cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
      cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
      cp(pages.flatMap(_.items) shouldBe expected)
      cp(past.items shouldBe empty)
      cp(past.totalNumberOfItems shouldBe 11L)
      cp.reportAll()
      succeed
    }

    res.transact(transactor)
  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = passwordHashed,
      accountTypeId = TgIds.UNASSIGNED_UUID,
      verified = true,
      enabled = true,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )
}
