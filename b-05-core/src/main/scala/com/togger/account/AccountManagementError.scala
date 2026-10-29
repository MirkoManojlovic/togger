package com.togger.account

import com.togger.common.TgError

object AccountManagementError {
  sealed trait AccountManagementError extends TgError

  case object AccountNotFound extends AccountManagementError {
    override def message: String = "Account not found"
  }

  case object EmailAlreadyInUse extends AccountManagementError {
    override def message: String = "An account with this email already exists"
  }

  case object UnknownAccountType extends AccountManagementError {
    override def message: String = "Unknown account type"
  }

  case object CannotDeleteOwnAccount extends AccountManagementError {
    override def message: String = "You cannot delete your own account"
  }

  case object AccountInUse extends AccountManagementError {
    override def message: String = "The account cannot be deleted because it still owns tasks, log entries or other data"
  }
}
