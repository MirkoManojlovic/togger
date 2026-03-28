package com.togger.account

import com.togger.account.HashedPasswordImpl.hashFactory
import com.togger.common.Password
import com.outr.scalapass.PBKDF2PasswordFactory

abstract sealed case class HashedPasswordImpl private(value: String) extends HashedPassword {
  def verify(attemptedPassword: Password): Boolean =
    hashFactory.verify(attemptedPassword.value, value)
}

object HashedPasswordImpl {
  private val hashFactory = new PBKDF2PasswordFactory

  def fromHashedString(hashedString: String): HashedPassword =
    new HashedPasswordImpl(hashedString) {}

  def fromPassword(password: Password): HashedPassword =
    new HashedPasswordImpl(hashFactory.hash(password.value)) {}

}
