package com.togger.account

import java.time.Instant
import java.util.UUID

import com.togger.common.{DbPageRequest, PaginatedResponse, Password}
import doobie.ConnectionIO

trait HashedPassword {
  val value: String

  def verify(attemptedPassword: Password): Boolean
}

case class Account(
  id: UUID,
  firstName: String,
  lastName: String,
  email: String,
  password: HashedPassword,
  accountTypeId: UUID,
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
  def findById(id: UUID): ConnectionIO[Account]
  def findByEmail(email: String): ConnectionIO[Account]
  def deleteById(id: UUID): ConnectionIO[Int]
  def update(account: Account, updateAt: Instant): ConnectionIO[Account]

  /** One page of the accounts whose first name, last name or email contains the term (ignoring case, `%` and `_` are
    * taken literally), by last name, first name.
    */
  def searchPaginated(term: String, page: DbPageRequest): ConnectionIO[PaginatedResponse[Account]]
}
