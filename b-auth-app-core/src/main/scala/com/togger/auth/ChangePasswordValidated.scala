package com.togger.auth

import cats.data.{EitherNec, ValidatedNec}
import cats.implicits._
import com.togger.auth.ChangePasswordError.{ChangePasswordError, PasswordsMustMatch}
import com.togger.common.Password.PasswordError
import com.togger.common.{Password, TgError}
import com.togger.dto.login.ChangePasswordDto

import java.util.UUID

case class ChangePasswordValidated(
  accountId: UUID,
  oldPassword: Password,
  newPassword: Password,
  confirmNewPassword: Password
) {

  override def toString: String =
    s"ChangePasswordValidated($accountId, ***, ***, ***)"
}

object ChangePasswordValidated {

  def fromDto(dto: ChangePasswordDto): EitherNec[TgError, ChangePasswordValidated] =
    (passwordsMatch(dto) match {
      case Left(e) => e.invalidNec
      case Right(dto) =>
        (
          validatePassword(dto.oldPassword),
          validatePassword(dto.newPassword),
          validatePassword(dto.confirmNewPassword)
        ).mapN { (oldPassword, newPassword, confirmNewPassword) =>
          ChangePasswordValidated(dto.accountId, oldPassword, newPassword, confirmNewPassword)
        }
    }).toEither

  private def validatePassword(passwordInput: String): ValidatedNec[PasswordError, Password] =
    Password.fromString(passwordInput).toValidated

  private def passwordsMatch(
    dto: ChangePasswordDto
  ): Either[ChangePasswordError, ChangePasswordDto] =
    if (dto.passwordsMatch)
      dto.asRight
    else PasswordsMustMatch.asLeft

}
