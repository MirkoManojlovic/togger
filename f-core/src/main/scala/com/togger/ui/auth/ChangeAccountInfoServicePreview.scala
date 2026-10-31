package com.togger.ui.auth

import cats.effect.IO
import com.togger.auth.AccountInfoError.EmailAlreadyInUse
import com.togger.auth.AccountInfoValidation
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.dto.login.AccountInfoDto
import com.togger.preview.{AccountsPreviewData, PreviewResponses}

import java.util.UUID

class ChangeAccountInfoServicePreview(accounts: AccountsPreviewData, loginRepo: LoginRepo)
    extends ChangeAccountInfoService {

  override def change(dto: AccountInfoDto): TgRespIO[AccountInfoDto] =
    PreviewResponses
      .authenticated(loginRepo) { accountId =>
        AccountInfoValidation.validate(dto).map(_.message) match {
          case Nil    => IO.defer(save(accountId, dto.copy(firstName = dto.firstName.trim, lastName = dto.lastName.trim, email = dto.email.trim)))
          case errors => PreviewResponses.badRequest(errors: _*)
        }
      }
      .map { result =>
        result.foreach(_.body.foreach(loginRepo.updateInfo))
        result
      }

  private def save(accountId: UUID, dto: AccountInfoDto): TgRespIO[AccountInfoDto] =
    accounts.accounts.find(accountId) match {
      case None => PreviewResponses.notFound
      case Some(_) if emailTakenByOther(accountId, dto.email) => PreviewResponses.badRequest(EmailAlreadyInUse.message)
      case Some(account) =>
        accounts.accounts.upsert(account.copy(firstName = dto.firstName, lastName = dto.lastName, email = dto.email))
        PreviewResponses.ok(dto)
    }

  private def emailTakenByOther(accountId: UUID, email: String): Boolean =
    accounts.byEmail(email).exists(_.id != accountId)
}
