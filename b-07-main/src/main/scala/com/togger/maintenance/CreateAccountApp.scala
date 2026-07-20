package com.togger.maintenance

import java.time._
import java.util.UUID

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import com.togger.Constants.{DB_NAME, DB_PASSWORD, DB_USERNAME}
import com.togger.account._
import com.togger.common.{Password, TgClockImpl, TgIds}
import com.togger.login.SessionTokenFactoryImpl
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}

object CreateAccountApp extends IOApp {

  private val hashedPassword =
    HashedPasswordImpl.fromPassword(Password.fromString("A!good_1_password_is_good").toOption.get)
  private val now = Instant.now

  override def run(args: List[String]): IO[ExitCode] = {

    val xa = Transactor.fromDriverManager[IO](
      driver = "org.postgresql.Driver",
      url = s"jdbc:postgresql:$DB_NAME",
      user = DB_USERNAME,
      password = DB_PASSWORD,
      logHandler = None
    )

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
        transactor = xa
      )

    def createAccountTypes: ConnectionIO[List[AccountType]] =
      Seq("developer", "manager", "qa")
        .map(accountName =>
          accountTypeRepo.insert(
            AccountType(
              id = UUID.randomUUID(),
              name = accountName,
              createdAt = now,
              updatedAt = now
            )
          )
        )
        .toList
        .sequence

    val accounts = Seq(
      createAccount("John", "Doe", "john@email.com"),
      createAccount("John", "Smith", "shark@email.com"),
      createAccount("Jack", "Curry", "bird@email.com"),
      createAccount("Steve", "Murry", "bird1@email.com"),
      createAccount("George", "Washington", "dog@email.com")
    )

    for {
      accountTypes <- createAccountTypes.transact(xa)
      _ <- accounts.map(account => accountService.createAccount(account)).sequence
    } yield ExitCode.Success

  }

  def createAccount(firstName: String, lastName: String, email: String) =
    Account(
      id = TgIds.UNASSIGNED_ACCOUNT_ID,
      firstName = firstName,
      lastName = lastName,
      email = email,
      password = hashedPassword,
      accountTypeId = UUID.randomUUID(),
      verified = true,
      enabled = true,
      createdAt = now,
      updatedAt = now
    )

}
