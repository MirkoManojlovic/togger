package com.togger.desktop

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.togger.account._
import com.togger.common.{Password, TgClockImpl}
import com.togger.login.SessionTokenFactoryImpl
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Transactor}

import java.time.Instant

class CreateAdminAccount(transactor: Transactor[IO]) {

  val hashedPassword = HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  val now = Instant.now

  def run() = {
    val accountTypeRepo = new AccountTypeRepo()
    val accountRepo = new AccountRepoImpl()
    val accountSessionRepo = new AccountSessionRepo()
    val sessionTokenFactory = new SessionTokenFactoryImpl
    val tgClock = new TgClockImpl

    val accountService =
      new AccountService(
        accountRepo = accountRepo,
        accountSessionRepo = accountSessionRepo,
        sessionTokenFactory = sessionTokenFactory,
        tgClock = tgClock,
        transactor = transactor
      )

    def createAccountTypes: ConnectionIO[List[AccountType]] =
      Seq("developer", "manager", "qa")
        .map(accountName =>
          accountTypeRepo.insert(
            AccountType(
              id = -1,
              name = accountName,
              createdAt = now,
              updatedAt = now
            )
          )
        )
        .toList
        .sequence

    val accounts = Seq(
      createAccount("John", "Doe", "admin@email.com")
    )

    for {
      accountTypes <- createAccountTypes.transact(transactor)
      _ <- accounts.map(account => accountService.createAccount(account)).sequence
    } yield ()
  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = -1,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = hashedPassword,
      accountTypeId = 1,
      verified = true,
      enabled = true,
      createdAt = now,
      updatedAt = now
    )

}
