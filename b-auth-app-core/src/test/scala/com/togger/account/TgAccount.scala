package com.togger.account

import cats.implicits._
import com.togger.common.{Password, TimeHelpers}
import com.togger.db.TgTestDbDrops
import doobie.ConnectionIO
import doobie.implicits._
import doobie.postgres.implicits._

trait TgAccount extends TgTestDbDrops { self: TimeHelpers =>
  lazy val passwordString = "A!good_1_password_is_good"
  lazy val email = "john@email.com"
  lazy val passwordHashed = HashedPasswordImpl.fromPassword(Password.fromString(passwordString).toOption.get)

  private lazy val accountRepo = new AccountRepoImpl
  lazy val accountTypeRepo = new AccountTypeRepo

  def createAccountTypes: ConnectionIO[List[AccountType]] =
    Seq("developer", "manager", "qa")
      .map(accountName =>
        accountTypeRepo.insert(
          AccountType(
            id = -1,
            name = accountName,
            createdAt = nowAsInstant,
            updatedAt = nowAsInstant
          )
        )
      )
      .toList
      .sequence

  def createAccount(accountTypeId: Long) = Account(
    id = -1L,
    firstName = "John",
    lastName = "Doe",
    email = email,
    password = passwordHashed,
    verified = false,
    enabled = true,
    accountTypeId = accountTypeId,
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  def createAccount: ConnectionIO[Account] =
    for {
      accountTypes <- createAccountTypes
      savedAccount <- accountRepo.insert(createAccount(accountTypes.head.id))
    } yield savedAccount

}
