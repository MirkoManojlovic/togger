package com.togger.common

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._

sealed abstract case class Password private (value: String)

object Password {

  private val passwordLength = 16
  private val validSpecialChars =
    Seq(
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

  def fromString(rawPassword: String): EitherNec[PasswordError, Password] =
    validatePassword(rawPassword).toEither

  def validatePassword(
    rawPassword: String
  ): ValidatedNec[PasswordError, Password] =
    (
      isNotCorrectLength(rawPassword),
      doesNotContainLowerCaseChar(rawPassword),
      doesNotContainUppercaseChar(rawPassword),
      doesNotContainSpecialChar(rawPassword),
      doesNotContainDigit(rawPassword)
    ).mapN((_, _, _, _, _) => new Password(rawPassword) {})

  private def isNotCorrectLength(rawPassword: String): ValidatedNec[PasswordError, String] =
    if (rawPassword.length >= passwordLength)
      rawPassword.validNec
    else
      InvalidLength(rawPassword).invalidNec

  private def doesNotContainLowerCaseChar(rawPassword: String): ValidatedNec[PasswordError, String] =
    if (rawPassword.matches(".*[a-z].*"))
      rawPassword.validNec
    else
      DoesNotContainLowercase(rawPassword).invalidNec

  private def doesNotContainUppercaseChar(rawPassword: String): ValidatedNec[PasswordError, String] =
    if (rawPassword.matches(".*[A-Z].*"))
      rawPassword.validNec
    else
      DoesNotContainUppercase(rawPassword).invalidNec

  private def doesNotContainDigit(rawPassword: String): ValidatedNec[PasswordError, String] =
    if (rawPassword.matches(".*\\d.*"))
      rawPassword.validNec
    else
      DoesNotContainDigit(rawPassword).invalidNec

  private def doesNotContainSpecialChar(rawPassword: String): ValidatedNec[PasswordError, String] = {

    def containsSpecialChar(rawPassword: String): Boolean =
      Password.validSpecialChars
        .map(char => rawPassword.contains(char))
        .fold(false)(_ || _)

    if (containsSpecialChar(rawPassword))
      rawPassword.validNec
    else
      DoesNotContainSpecialChar(rawPassword).invalidNec

  }
  sealed trait PasswordError extends TgError

  case class InvalidLength(rawPassword: String) extends PasswordError {
    override val message: String = s"Password must be at least 16 characters long"
  }

  case class DoesNotContainLowercase(rawPassword: String) extends PasswordError {
    override val message: String = "Password must contain a lowercase char"
  }

  case class DoesNotContainUppercase(rawPassword: String) extends PasswordError {
    override val message: String = "Password must contain an uppercase char"
  }

  case class DoesNotContainDigit(rawPassword: String) extends PasswordError {
    override val message: String = "Password must contain a digit"
  }

  case class DoesNotContainSpecialChar(rawPassword: String) extends PasswordError {
    override val message: String = s"Password must contain a special char: ${Password.validSpecialChars}"
  }

}
