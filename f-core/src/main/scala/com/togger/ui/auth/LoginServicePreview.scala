package com.togger.ui.auth

import cats.effect.IO
import com.togger.common.TgJsonFailure
import com.togger.dto.login.{LoginDto, LoginResponseDto}
import com.togger.preview.{AccountsPreviewData, PreviewAccount}

import java.util.UUID

class LoginServicePreview(accounts: AccountsPreviewData, loginRepo: LoginRepo) extends LoginService {

  override def login(dto: LoginDto): IO[Either[TgJsonFailure, Unit]] =
    IO {
      matchingAccount(dto).toRight(TgJsonFailure.badRequestString("Invalid credentials")).map { account =>
        loginRepo.save(
          LoginResponseDto(account.id, UUID.randomUUID().toString, account.firstName, account.lastName, account.email)
        )
      }
    }

  private def matchingAccount(dto: LoginDto): Option[PreviewAccount] =
    accounts.byEmail(dto.email).filter(account => account.enabled && account.password == dto.password)
}
