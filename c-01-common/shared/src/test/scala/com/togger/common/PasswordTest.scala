package com.togger.common

import com.togger.common.Password._
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import scribe.Logging

class PasswordTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with TableDrivenPropertyChecks
    with Logging
    with NonEmptyChainHelpers {

  val invalidLengthPassword = "m1M_!"
  val doesNotContainLowercase = "BADPASSWORD123456!"
  val doesNotContainUppercase = "badpassword123456!"
  val goodPassword = "A!good_1_password_is_good"

  describe("Password") {

    it("should return errors for wrong passwords") {
      val wrongPasswords = Table(
        ("passwordInput", "result"),
        (invalidLengthPassword, InvalidLength(invalidLengthPassword)),
        (doesNotContainLowercase, DoesNotContainLowercase(doesNotContainLowercase)),
        (doesNotContainUppercase, DoesNotContainUppercase(doesNotContainUppercase))
      )

      forAll(wrongPasswords) { (passwordInput: String, error: PasswordError) =>
        Password.fromString(passwordInput).left.value.asList should contain only error
      }
    }

    it("should return proper password") {
      val passwordInput = "A!good_1_password_is_good"
      Password.fromString(passwordInput).value.value shouldBe passwordInput
    }

    it("should return valid password for the list of special chars") {
      val validSpecialChars = Seq(
        "!",
        "@",
        "#",
        "$",
        "%",
        "^",
        "&",
        "(",
        ")",
        "{",
        "}",
        "[",
        "]",
        ":",
        "\"",
        ";",
        "'",
        "<",
        ">",
        ",",
        ".",
        "?",
        "~",
        "`",
        "_",
        "+",
        "-",
        "=",
        "|",
        " ",
        "\\",
        "/"
      )

      val basePassword = "Notyet123goodpassword"
      Password.fromString(basePassword).left.value.asList should contain only DoesNotContainSpecialChar(basePassword)

      validSpecialChars
        .map(specialChar => basePassword + specialChar)
        .map(passwordInput => (passwordInput, Password.fromString(passwordInput)))
        .foreach { case (passwordInput, password) =>
          logger.info(s"Input password: $passwordInput")
          password.value.value shouldBe passwordInput
        }

    }

  }

}
