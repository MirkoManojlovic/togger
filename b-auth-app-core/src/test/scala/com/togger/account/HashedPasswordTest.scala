package com.togger.account

import com.togger.TgWordSpecJvm
import com.togger.common.Password
import com.outr.scalapass.PBKDF2PasswordFactory
import org.scalatest.EitherValues

class HashedPasswordTest extends TgWordSpecJvm with EitherValues {

  val hashFactory = new PBKDF2PasswordFactory

  "HashedPassword" should {
    "create password from already hashed string" in {
      val hashedString = "hashedString"

      HashedPasswordImpl.fromHashedString(hashedString).value shouldBe hashedString
    }

    "create password from password type" in {
      val passwordString = "Password123456##"
      val password = Password.fromString(passwordString).toOption.get
      val hashedPassword = HashedPasswordImpl.fromPassword(password)

      val anotherPasswordString = "AnotherPassword789$$"
      val anotherPassword = Password.fromString(anotherPasswordString).toOption.get

      hashedPassword.verify(password) shouldBe true
      hashedPassword.verify(anotherPassword) shouldBe false
    }
  }

}
