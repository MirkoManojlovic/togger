package com.togger.desktop

import cats.effect.IO
import cats.implicits._
import com.togger.account._
import com.togger.common.{Password, TgClockImpl}
import com.togger.login.SessionTokenFactoryImpl
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.{ConnectionIO, Transactor}

class CreateAdminAccount(transactor: Transactor[IO]) {
  private val accountTypeRepo = new AccountTypeRepo()
  private val accountRepo = new AccountRepoImpl()
  private val accountSessionRepo = new AccountSessionRepo()
  private val sessionTokenFactory = new SessionTokenFactoryImpl
  private val tgClock = new TgClockImpl

  private val accountService =
    new AccountService(
      accountRepo = accountRepo,
      accountSessionRepo = accountSessionRepo,
      sessionTokenFactory = sessionTokenFactory,
      tgClock = tgClock,
      transactor = transactor
    )

  private val now = tgClock.currentInstant
  private val hashedPassword =
    HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  private val accountTypes = Seq("developer", "manager", "qa")

  def run: IO[Unit] = {
    val accounts = Seq(
      createAccount("John", "Doe", "admin@email.com")
    )

    for {
      accountTypes <- createAccountTypes.transact(transactor)
      _ <- accounts.map(account => accountService.createAccount(account)).sequence
    } yield ()
  }

  private def createAccountTypes: ConnectionIO[List[AccountType]] =
    accountTypes
      .map(accountTypeName =>
        accountTypeRepo.insert(
          AccountType(
            id = -1,
            name = accountTypeName,
            createdAt = now,
            updatedAt = now
          )
        )
      )
      .toList
      .sequence

  private def createAccount(firstName: String, lastName: String, email: String): Account =
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
