package com.togger.auth

import com.togger.common.TgError
import com.togger.dto.login.AccountInfoDto

sealed trait AccountInfoError extends TgError

object AccountInfoError {
  case class BlankField(field: String) extends AccountInfoError {
    override def message: String = s"$field can not be blank"
  }

  case class FieldTooLong(field: String, maxLength: Int) extends AccountInfoError {
    override def message: String = s"$field can not be longer than $maxLength characters"
  }

  case object InvalidEmail extends AccountInfoError {
    override def message: String = "Email is not a valid email address"
  }

  case object EmailAlreadyInUse extends AccountInfoError {
    override def message: String = "Email is already in use by another account"
  }
}

/** Shared by the backend (authoritative) and the frontend form (immediate feedback). */
object AccountInfoValidation {
  import AccountInfoError._

  val MaxLength: Int = 50

  private val emailPattern = "^[^@\\s]+@[^@\\s]+$".r

  def firstName(value: String): List[AccountInfoError] = text("First name", value)

  def lastName(value: String): List[AccountInfoError] = text("Last name", value)

  def email(value: String): List[AccountInfoError] =
    text("Email", value) ++ (if (value.isBlank || emailPattern.matches(value.trim)) Nil else List(InvalidEmail))

  def validate(dto: AccountInfoDto): List[AccountInfoError] =
    firstName(dto.firstName) ++ lastName(dto.lastName) ++ email(dto.email)

  private def text(field: String, value: String): List[AccountInfoError] =
    if (value.isBlank) List(BlankField(field))
    else if (value.trim.length > MaxLength) List(FieldTooLong(field, MaxLength))
    else Nil
}
