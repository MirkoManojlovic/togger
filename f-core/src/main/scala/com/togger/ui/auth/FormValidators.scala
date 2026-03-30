package com.togger.ui.auth

import com.togger.common.Password

trait FormErrors {
  def hasErrors: Boolean = formErrors.nonEmpty

  def formErrors: Seq[String] = additionalErrors ++ Seq.empty

  def additionalErrors: Seq[String] = Seq.empty
}

trait PasswordValidator extends FormErrors {
  def password: String

  def passwordValid: Seq[String] =
    Password
      .fromString(password)
      .swap
      .toSeq
      .flatMap(errorsAsChain => errorsAsChain.toNonEmptyList.toList.map(_.message))

  override def formErrors: Seq[String] = super.formErrors ++ passwordValid

  def passwordsMatch(password: String, confirmedPassword: String): Seq[String] =
    (password, confirmedPassword) match {
      case ("", "")           => Seq.empty
      case (p, cp) if p == cp => Seq.empty
      case (_, _)             => Seq("Passwords must match!")
    }
}

trait NonBlankValidator {

  def nonBlankValid(input: String)(whatCantBeBlank: String): Seq[String] =
    if (input.isBlank) Seq(s"$whatCantBeBlank can not be blank")
    else Seq.empty

}
