package com.togger.ui.auth

import com.togger.auth.AuthAccountFe
import com.togger.auth.UserRole.NoPermissionsUserRole
import com.togger.dto.login.LoginResponseDto
import com.togger.logging.TgLogging
import com.togger.ui.localstorage.LocalStorage
import com.raquo.airstream.core.Signal
import com.raquo.laminar.api.L.Var
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps

case class TokenStorageKeys(accessTokenKey: String, refreshTokenKey: String)

class LoginRepo(
  localStorage: LocalStorage,
  tokenStorageKeys: TokenStorageKeys
) extends TgLogging {

  import tokenStorageKeys._

  private lazy val userAuthState: Var[Option[AuthAccountFe]] =
    Var(initialAuthState)

  private lazy val initialAuthState: Option[AuthAccountFe] =
    getAccountFromLocalStorage

  def authenticatedSignal: Signal[Option[AuthAccountFe]] =
    userAuthState.signal

  def save(dto: LoginResponseDto): Unit = {
    val authAccount = AuthAccountFe(
      id = dto.accountId,
      sessionId = dto.sessionId,
      userRole = NoPermissionsUserRole,
      firstName = Some(dto.firstName),
      lastName = Some(dto.lastName),
      email = Some(dto.email)
    )
    localStorage.setItem(accessTokenKey, authAccount.asJson.noSpaces)
    userAuthState.set(Some(authAccount))
  }

  def delete(): Unit = {
    localStorage
      .removeItem(accessTokenKey)
    userAuthState.set(None)
  }

  def authenticated: Boolean =
    account.fold(false)(_ => true)

  def notAuthenticated: Boolean =
    account.fold(true)(_ => false)

  def account: Option[AuthAccountFe] =
    getAccountFromLocalStorage.fold {
      setState(None)
      Option.empty[AuthAccountFe]
    } { authAccount =>
      setState(Some(authAccount))
      Some(authAccount)
    }

  private def getAccountFromLocalStorage: Option[AuthAccountFe] =
    localStorage
      .getItem(accessTokenKey)
      .flatMap { authAccountAsString =>
        decode[AuthAccountFe](authAccountAsString)
          .fold(
            e => {
              logger.debug("Couldn't decode auth account as string", e)
              None
            },
            authAccountFe => Some(authAccountFe)
          )

      }
      .orElse(None)

  private def setState(authAccountOpt: Option[AuthAccountFe]): Unit =
    userAuthState.now() match {
      case None => ()
      case storedAccount @ Some(_) =>
        if (storedAccount == authAccountOpt) ()
        else userAuthState.set(authAccountOpt)
    }

}
