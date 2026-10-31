package com.togger.ui.auth

import cats.effect.IO
import com.togger.common.Password
import com.togger.common.TgResponseTypes.TgRespEmptyBodyIO
import com.togger.dto.login.ChangePasswordDto
import com.togger.preview.{AccountsPreviewData, PreviewResponses}

class ChangePasswordServicePreview(accounts: AccountsPreviewData, loginRepo: LoginRepo) extends ChangePasswordService {

  override def change(dto: ChangePasswordDto): TgRespEmptyBodyIO =
    PreviewResponses.authenticated(loginRepo) { _ =>
      validationErrors(dto) match {
        case Nil    => IO.defer(updatePassword(dto))
        case errors => PreviewResponses.badRequest(errors: _*)
      }
    }

  private def validationErrors(dto: ChangePasswordDto): List[String] = {
    val passwordErrors =
      Password.fromString(dto.newPassword).swap.toOption.toList.flatMap(_.toNonEmptyList.toList.map(_.message))
    val mismatch = if (dto.passwordsMatch) Nil else List("New password and confirm new password must match")
    mismatch ++ passwordErrors
  }

  private def updatePassword(dto: ChangePasswordDto): TgRespEmptyBodyIO =
    accounts.accounts.find(dto.accountId) match {
      case None => PreviewResponses.badRequest("Invalid account id")
      case Some(account) if account.password != dto.oldPassword =>
        PreviewResponses.badRequest("Old password is incorrect")
      case Some(account) =>
        accounts.accounts.upsert(account.copy(password = dto.newPassword))
        PreviewResponses.okEmpty("Password successfully changed")
    }
}
