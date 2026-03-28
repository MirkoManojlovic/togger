package com.togger.auth

import cats.data.NonEmptyChain
import cats.effect.IO
import com.togger.account.{Account, AccountRepoImpl, HashedPasswordImpl, InvalidCredentials}
import com.togger.auth.ChangePasswordError.{InvalidAccountId, OldPasswordIncorrect}
import com.togger.common.TgJsonSuccess.TgEmptyBody
import com.togger.common.TgResponseTypes.TgRespEmptyBodyIO
import com.togger.common._
import com.togger.dto.login.ChangePasswordDto
import com.togger.logging.TgLogging
import doobie.Transactor
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd

class PasswordUpdate {
  def run(account: Account, newPassword: Password): Account =
    account.copy(password = HashedPasswordImpl.fromPassword(newPassword))
}

class ChangePasswordLogic(
                           accountRepo: AccountRepoImpl,
                           passwordUpdate: PasswordUpdate,
                           tgClock: TgClock,
                           transactor: Transactor[IO]
) extends TgLogging {

  def change(dto: ChangePasswordDto): TgRespEmptyBodyIO = {
    val validatedData = ChangePasswordValidated.fromDto(dto)
    validatedData.fold(toChangePasswordError, toChangePasswordSuccess)
  }

  private def toChangePasswordError(errors: NonEmptyChain[TgError]): TgRespEmptyBodyIO =
    TgJsonFailure.badRequestLeftIO(TgJsonError.fromErrorsNec(errors))

  private def toChangePasswordSuccess(changePasswordValidated: ChangePasswordValidated): TgRespEmptyBodyIO = {
    val res = for {
      account <- accountRepo
        .findById(changePasswordValidated.accountId)
        .transact(transactor)
      result <-
        if (account.password.verify(changePasswordValidated.oldPassword))
          accountRepo
            .update(
              account = passwordUpdate.run(account, changePasswordValidated.newPassword),
              updatedAt = tgClock.currentInstant
            )
            .transact(transactor)
            .map(_ => TgJsonSuccess.justMessageRight("Password successfully changed"))
            .handleError { e =>
              logger.error(s"An error occurred while updating password, [error=$e]")
              TgJsonFailure.internalErrorLeft[TgEmptyBody](Some("An error occurred while updating password"))
            }
        else
          TgJsonFailure.badRequestLeftIO[TgEmptyBody](TgJsonError.fromError(OldPasswordIncorrect))

    } yield result

    res.recoverWith { case UnexpectedEnd =>
       TgJsonFailure.badRequestLeftIO[TgEmptyBody](TgJsonError.fromError(InvalidAccountId))
    }
  }

}
