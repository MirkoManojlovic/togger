package com.togger.account

import cats.implicits._
import com.togger.common.TimeHelpers
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

    "find all accounts that match either by first name, last name or email" in { transactor =>
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
        birdResult <- accountRepo.findByFirstNameLastNameOrEmail("bird")
        steveResult <- accountRepo.findByFirstNameLastNameOrEmail("sTeVe")
        urryResult <- accountRepo.findByFirstNameLastNameOrEmail("urry")
      } yield {
        val cp = new Checkpoint
        cp(birdResult should have size 2)
        cp(steveResult should have size 1)
        cp(birdResult should have size 3)
        cp(birdResult shouldBe savedAccounts.filter(_.email.contains("bird")))
        cp(steveResult shouldBe savedAccounts.filter(_.firstName.contains("Steve")))
        birdResult shouldBe savedAccounts.filter(_.lastName.contains("urry"))
      }

      res.transact(transactor)

    }
  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = -1,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = passwordHashed,
      accountTypeId = 1,
      verified = true,
      enabled = true,
      createdAt = nowAsInstant,
      updatedAt = nowAsInstant
    )

}
