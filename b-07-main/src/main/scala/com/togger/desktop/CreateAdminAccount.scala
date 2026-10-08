package com.togger.desktop

import java.util.UUID

import cats.effect.IO
import cats.implicits._
import com.togger.account._
import com.togger.common.{Password, TgClockImpl, TgIds}
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
  private val accountTypes = Seq("developer", "manager", "qa", AdminCheck.AdminAccountTypeName)

  def run: IO[Unit] = {
    for {
      accountTypes <- createAccountTypes.transact(transactor)
      adminAccountType = accountTypes.find(_.name == AdminCheck.AdminAccountTypeName).get
      _ <- Seq(
        createAccount("John", "Doe", "admin@email.com", adminAccountType.id)
      ).map(account => accountService.createAccount(account)).sequence
    } yield ()
  }

  private def createAccountTypes: ConnectionIO[List[AccountType]] =
    accountTypes
      .map(accountTypeName =>
        accountTypeRepo.insert(
          AccountType(
            id = TgIds.UNASSIGNED_UUID,
            name = accountTypeName,
            createdAt = now,
            updatedAt = now
          )
        )
      )
      .toList
      .sequence

  private def createAccount(firstName: String, lastName: String, email: String, accountTypeId: UUID): Account =
    Account(
      id = TgIds.UNASSIGNED_UUID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = hashedPassword,
      accountTypeId = accountTypeId,
      verified = true,
      enabled = true,
      createdAt = now,
      updatedAt = now
    )

}
