package com.togger.account

import cats.implicits._
import com.togger.common.{DbPageRequest, PageRequest, TgIds, TimeHelpers}
import com.togger.dto.login.{DeviceInfo, LoginDto}
import doobie.implicits._
import doobie.postgres.implicits._

class AccountRepoTest extends AccountTgDbSpec with TimeHelpers with TgAccount {

  val accountRepo = new AccountRepoImpl

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        foundAccount1 <- accountRepo.findById(savedAccount.id)
        newUpdatedAt = nowAsInstant
        updatedAccount <- accountRepo.update(savedAccount.copy(firstName = "new first name"), newUpdatedAt)
        foundAccount2 <- accountRepo.findById(savedAccount.id)
        _ <- accountRepo.deleteById(foundAccount2.id)
        foundAccount3 <- accountRepo.findById(savedAccount.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedAccount shouldBe foundAccount1)
        cp(updatedAccount shouldBe foundAccount2)
        cp.reportAll()
        foundAccount3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "find account by email" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        foundAccount <- accountRepo.findByEmail(savedAccount.email).attempt
      } yield {
        foundAccount shouldBe Right(savedAccount)
      }

      res.transact(transactor)
    }

    "return an error when email does not exist" in { transactor =>
      val dto = LoginDto(
        email = "nonexistent@example.com",
        password = passwordString,
        deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0")
      )

      val res = for {
        result <- accountRepo.findByEmail(dto.email).attempt
      } yield {
        result.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "search accounts by first name, last name or email, ignoring case" in { transactor =>
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
        birdResult <- accountRepo.searchPaginated("bird", pageOf(1, 20))
        steveResult <- accountRepo.searchPaginated("sTeVe", pageOf(1, 20))
        urryResult <- accountRepo.searchPaginated("urry", pageOf(1, 20))
        noResult <- accountRepo.searchPaginated("nobody", pageOf(1, 20))
      } yield {
        val cp = new Checkpoint
        cp(birdResult.items shouldBe savedAccounts.filter(_.email.contains("bird")).sortBy(_.lastName))
        cp(steveResult.items shouldBe savedAccounts.filter(_.firstName == "Steve"))
        cp(urryResult.items shouldBe savedAccounts.filter(_.lastName.contains("urry")).sortBy(_.lastName))
        cp(urryResult.totalNumberOfItems shouldBe 2L)
        cp(noResult.items shouldBe empty)
        cp(noResult.totalNumberOfItems shouldBe 0L)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }

    "treat percent, underscore and backslash in the search term literally" in { transactor =>
      val res = for {
        accountTypes <- createAccountTypes
        _ <- accountRepo.insert(
          createAccount("Percent", "100%", "percent@email.com").copy(accountTypeId = accountTypes.head.id)
        )
        _ <- accountRepo.insert(
          createAccount("Under", "Score", "under_score@email.com").copy(accountTypeId = accountTypes.head.id)
        )
        _ <- accountRepo.insert(
          createAccount("Plain", "Name", "underXscore@email.com").copy(accountTypeId = accountTypes.head.id)
        )
        percent <- accountRepo.searchPaginated("%", pageOf(1, 20))
        underscore <- accountRepo.searchPaginated("under_score", pageOf(1, 20))
        backslash <- accountRepo.searchPaginated("\\", pageOf(1, 20))
      } yield {
        val cp = new Checkpoint
        cp(percent.items.map(_.lastName) shouldBe List("100%"))
        cp(underscore.items.map(_.lastName) shouldBe List("Score"))
        cp(backslash.items shouldBe empty)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }

    "split 11 matching accounts over 3 pages of 5, by last name, first name, without a duplicate" in { transactor =>
      val res = for {
        accountTypes <- createAccountTypes
        // two accounts share a last name, so the first name decides; inserted in reverse, so not in insertion order
        savedAccounts <- (0 to 10).toList.reverse
          .map(index =>
            createAccount(
              f"First $index%02d",
              if (index < 2) "Same" else f"Last $index%02d",
              s"match$index@email.com"
            )
          )
          .map(account => accountRepo.insert(account.copy(accountTypeId = accountTypes.head.id)))
          .sequence
        _ <- accountRepo.insert(
          createAccount("Other", "Person", "other@email.com").copy(accountTypeId = accountTypes.head.id)
        )
        pages <- (1 to 3).toList.traverse(number => accountRepo.searchPaginated("match", pageOf(number, 5)))
        past <- accountRepo.searchPaginated("match", pageOf(4, 5))
      } yield {
        val cp = new Checkpoint
        cp(pages.map(_.items.size) shouldBe List(5, 5, 1))
        cp(pages.map(_.totalNumberOfItems).distinct shouldBe List(11L))
        cp(pages.map(_.totalNumberOfPages).distinct shouldBe List(3L))
        cp(pages.map(_.page) shouldBe List(1, 2, 3))
        cp(pages.flatMap(_.items) shouldBe savedAccounts.sortBy(account => (account.lastName, account.firstName)))
        cp(past.items shouldBe empty)
        cp(past.totalNumberOfItems shouldBe 11L)
        cp.reportAll()
        succeed
      }

      res.transact(transactor)
    }
  }

  private def pageOf(pageNumber: Int, size: Int) =
    DbPageRequest.fromPageRequest(PageRequest(pageNumber, size)).toOption.get

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
