package com.togger.account

import com.togger.common.Password
import doobie.ConnectionIO

import java.time.Instant

trait HashedPassword {
  val value: String

  def verify(attemptedPassword: Password): Boolean
}

case class Account(
  id: Long,
  firstName: String,
  lastName: String,
  email: String,
  password: HashedPassword,
  accountTypeId: Long,
  verified: Boolean,
  enabled: Boolean,
  createdAt: Instant,
  updatedAt: Instant
) {

  override def toString: String =
    s"Account($id, $firstName, $lastName, $email, ***, $verified, $enabled, $accountTypeId, $createdAt, $updatedAt)"

}

trait AccountRepo {
  def insert(account: Account): ConnectionIO[Account]
  def findById(id: Long): ConnectionIO[Account]
  def findByEmail(email: String): ConnectionIO[Account]
  def deleteById(id: Long): ConnectionIO[Int]
  def update(account: Account, updateAt: Instant): ConnectionIO[Account]
  def findByFirstNameLastNameOrEmail(term: String): ConnectionIO[List[Account]]
}
