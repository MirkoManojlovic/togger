package com.togger.account

import cats.effect.IO

import java.util.UUID

/** Who may use the endpoints that read across tenants. */
trait AdminCheck {
  def isAdmin(accountId: UUID): IO[Boolean]
}

object AdminCheck {

  /** The account type that makes an account an admin. */
  val AdminAccountTypeName: String = "admin"
}
