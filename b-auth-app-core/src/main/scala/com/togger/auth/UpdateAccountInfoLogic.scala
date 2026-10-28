package com.togger.auth

import cats.effect.IO
import com.togger.account.AccountRepoImpl
import com.togger.auth.AccountInfoError.EmailAlreadyInUse
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common._
import com.togger.dto.login.AccountInfoDto
import com.togger.logging.TgLogging
import doobie.Transactor
import doobie.implicits._
import doobie.postgres.sqlstate
import doobie.util.invariant.UnexpectedEnd

import java.util.UUID

class UpdateAccountInfoLogic(
  accountRepo: AccountRepoImpl,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def update(accountId: UUID, dto: AccountInfoDto): TgRespIO[AccountInfoDto] =
    AccountInfoValidation.validate(dto) match {
      case Nil    => save(accountId, dto.copy(firstName = dto.firstName.trim, lastName = dto.lastName.trim, email = dto.email.trim))
      case errors => TgJsonFailure.badRequestLeftIO(errors.flatMap(TgJsonError.fromError).toSet)
    }

  private def save(accountId: UUID, dto: AccountInfoDto): TgRespIO[AccountInfoDto] =
    (for {
      account <- accountRepo.findById(accountId)
      updated <- accountRepo.update(
        account.copy(firstName = dto.firstName, lastName = dto.lastName, email = dto.email),
        tgClock.currentInstant
      )
    } yield TgJsonSuccess.justBodyRight(AccountInfoDto(updated.firstName, updated.lastName, updated.email)))
      .transact(transactor)
      .recoverWith {
        case UnexpectedEnd =>
          TgJsonFailure.notFoundLeftIO[AccountInfoDto](Some("Account not found"))
        case e: java.sql.SQLException if e.getSQLState == sqlstate.class23.UNIQUE_VIOLATION.value =>
          TgJsonFailure.badRequestLeftIO[AccountInfoDto](TgJsonError.fromError(EmailAlreadyInUse))
        case e =>
          IO(logger.error(s"An error occurred while updating account info, [error=$e]")) *>
            TgJsonFailure.internalErrorLeftIO[AccountInfoDto](Some("An error occurred while updating account info"))
      }
}
