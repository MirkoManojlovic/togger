package com.togger.login

import cats.effect.IO
import com.togger.account.AccountService
import com.togger.auth.{BaseEndpoint, ChangePasswordLogic, SecuredEndpoint}
import com.togger.common.TgResponseTypes.{TgAuth, TgRespEmptyBodyIO}
import com.togger.common.{TgClock, TgJsonFailure, TgJsonSuccess}
import com.togger.dto.login.{ChangePasswordDto, ExpireSessionResult, LoginResponseDto, LogoutEveryoneDto}
import com.togger.logging.TgLogging

import java.time.temporal.ChronoUnit

class LoginController(
  accountService: AccountService,
  changePasswordLogic: ChangePasswordLogic,
  securedEndpoint: SecuredEndpoint,
  tgClock: TgClock
) extends TgLogging {

  val endpoints = new AuthEndpoints(BaseEndpoint.baseEndpoint, securedEndpoint)

  val login =
    endpoints.loginEndpoint.serverLogic { loginDto =>
      accountService
        .login(loginDto)
        .map(responseDto => {
          logger.info(s"Here, $responseDto")
          TgJsonSuccess.justBodyRight(responseDto)
        })
        .handleError { e =>
          logger.error(s"Login failed with error, $e")
          TgJsonFailure.badRequestStringLeft[LoginResponseDto]("Invalid credentials")
        }
    }

  val expireOldSessions =
    endpoints.expireSessions.serverLogic { _ =>
      accountService
        .expireSessionsOlderThan(tgClock.currentInstant.minus(1, ChronoUnit.DAYS))
        .map(result => TgJsonSuccess.justBodyRight(result))
        .handleError { e =>
          logger.error("An error occurred when trying to expire sessions", e)
          TgJsonFailure.internalErrorLeft[ExpireSessionResult](Some("Failed to expire sessions"))
        }
    }

  val logoutEveryone =
    endpoints.logoutEveryone.serverLogic { password =>
      if (password == "mirule123")
        accountService.logoutEveryone
          .map(result => TgJsonSuccess.justBodyRight(LogoutEveryoneDto(result)))
          .handleError { e =>
            logger.error("An error occurred when trying to logout everyone", e)
            TgJsonFailure.internalErrorLeft[LogoutEveryoneDto](Some("Failed to logout everyone"))
          }
      else IO(TgJsonFailure.badRequestStringLeft[LogoutEveryoneDto]("Invalid password"))
    }

  val exampleAuth =
    endpoints.exampleAuth
      .serverSecurityLogic(token =>
        accountService
          .isSessionExpired(token)
          .map(isExpired =>
            if (isExpired.isEmpty) TgJsonFailure.badRequestStringLeft("401 mirule")
            else Right("da shit")
          )
      )
      .serverLogic { _ => _ =>
        IO(TgJsonSuccess.justMessageRight("yeah"))
      }

  val changePassword =
    endpoints.changePassword.serverLogic(changePasswordFunc)

  private def changePasswordFunc: TgAuth => ChangePasswordDto => TgRespEmptyBodyIO =
    _ => dto => changePasswordLogic.change(dto)

}
