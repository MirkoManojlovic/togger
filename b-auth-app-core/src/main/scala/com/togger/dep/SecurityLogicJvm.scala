package com.togger.dep

import cats.effect.IO
import cats.implicits._
import com.togger.account.AccountSessionRepo
import com.togger.auth.{AuthAccount, SecurityLogic}
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{TgJsonFailure, TgJsonSuccess}
import com.togger.logging.TgLogging
import doobie.Transactor
import doobie.implicits._

class SecurityLogicJvm(accountSessionRepo: AccountSessionRepo, transactor: Transactor[IO])
    extends SecurityLogic
    with TgLogging {

  override def securityLogic(token: String): TgRespIO[AuthAccount] = {
    accountSessionRepo
      .findByTokenWithAccountNotSoftDeleted(token)
      .map { case (session, account) =>
        AuthAccount(
          id = account.id,
          firstName = account.firstName,
          lastName = account.lastName,
          email = account.email,
          createdAt = account.createdAt,
          updatedAt = account.updatedAt,
          deviceInfo = session.deviceInfo,
          sessionCreatedAt = session.createdAt,
          sessionUpdatedAt = session.updatedAt
        )
      }
      .attempt
      .map { either =>
        either.fold(
          error => {
            logger.warn("Failed to find account for token", error)
            TgJsonFailure.unauthenticatedLeft[AuthAccount](None)
          },
          authAccount => {
            TgJsonSuccess.justBodyRight(authAccount)
          }
        )
      }
      .transact(transactor)
  }
}
