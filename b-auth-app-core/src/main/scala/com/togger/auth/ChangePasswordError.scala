package com.togger.auth

import com.togger.common.TgError

object ChangePasswordError {
  sealed trait ChangePasswordError extends TgError

  case object PasswordsMustMatch extends ChangePasswordError {
    override def message: String = "New password and confirm new password must match"
  }

  case object OldPasswordIncorrect extends ChangePasswordError {
    override def message: String = "Old password is incorrect"
  }

  case object InvalidAccountId extends ChangePasswordError {
    override def message: String = "Invalid account id"
  }
}
