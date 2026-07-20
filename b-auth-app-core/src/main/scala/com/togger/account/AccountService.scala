package com.togger.account

import java.time.{Duration, Instant}
import java.util.UUID
import cats.effect.IO
import cats.implicits.catsSyntaxApplicativeId
import com.togger.common.{Password, TgClock, TgIds}
import com.togger.dto.login.{ExpireSessionResult, LoginDto, LoginResponseDto}
import com.togger.logging.TgLogging
import com.togger.login.SessionTokenFactory
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import doobie.{ConnectionIO, Transactor}

case object InvalidCredentials extends RuntimeException

class AccountService(
  accountRepo: AccountRepo,
  accountSessionRepo: AccountSessionRepo,
  sessionTokenFactory: SessionTokenFactory,
  tgClock: TgClock,
  transactor: Transactor[IO]
) extends TgLogging {

  def createAccount(account: Account): IO[Account] =
    accountRepo
      .insert(account)
      .transact(transactor)

  def login(loginDto: LoginDto): IO[LoginResponseDto] =
    accountRepo
      .findByEmail(loginDto.email)
      .map { accountFromDb =>
        val passwordVerifiedResult = Password.fromString(loginDto.password)

        passwordVerifiedResult.fold(
          errors => {
            logger.error(s"Invalid password was provided: ${errors.toNonEmptyList.toList}")
            accountFromDb -> false
          },
          password => accountFromDb -> accountFromDb.password.verify(password)
        )
      }
      .flatMap { case (accountFromDb, passwordsMatch) =>
        if (passwordsMatch) {
          val accountSession =
            AccountSession(
              id = TgIds.UNASSIGNED_UUID,
              accountId = accountFromDb.id,
              sessionToken = sessionTokenFactory.generateSessionToken,
              deviceInfo = loginDto.deviceInfo.toString,
              createdAt = tgClock.currentInstant,
              updatedAt = tgClock.currentInstant
            )

          accountSessionRepo
            .insert(accountSession)
            .map(accountSession =>
              LoginResponseDto(accountId = accountSession.accountId, sessionId = accountSession.sessionToken)
            )
        } else {
          throw InvalidCredentials
        }
      }
      .transact(transactor)
      .recoverWith { case UnexpectedEnd => throw InvalidCredentials }

  def expireSessionsOlderThan(date: Instant): IO[ExpireSessionResult] = {
    logger.info(s"Expiring sessions older than [date=$date]")
    (for {
      numberOfSessionsDeleted <- accountSessionRepo.deleteSoftByDate(date)
      numberOfSessionsInDb <- accountSessionRepo.countNotSoftDeleted()
    } yield {
      logger.info(s"[numberOfSessionsDeleted=$numberOfSessionsDeleted], [numberOfSessionsInDb=$numberOfSessionsInDb]")
      ExpireSessionResult(deleted = numberOfSessionsDeleted, inDb = numberOfSessionsInDb)
    }).transact(transactor)
  }

  def logoutEveryone: IO[Int] = {
    logger.info("Logging out everyone")
    accountSessionRepo.deleteAllSoft().transact(transactor)
  }

  def findByToken(token: String): IO[AccountSession] =
    accountSessionRepo.findByTokenNotSoftDeleted(token).transact(transactor)

  def isSessionExpired(sessionToken: String): IO[Option[AccountSession]] = {
    logger.info(s"Checking if session has expired, for [sessionToken=${sessionToken.take(5)}]")
    for {
      accountSessionInDb <- accountSessionRepo.findByTokenNotSoftDeleted(sessionToken).transact(transactor).attempt
      _ = logger.info(accountSessionInDb.toString)
      isExpired <-
        (if (accountSessionInDb.isLeft) {
           None.pure[ConnectionIO]
         } else {
           val accountSession = accountSessionInDb.toOption.get
           logger.info(s"Session exists, checking if it has expired, [createdAt=${accountSession.createdAt}]")
           if (accountSession.createdAt.isBefore(tgClock.currentInstant.minus(Duration.ofHours(12)))) {
             logger.info("Session has expired, deleting it")
             accountSessionRepo.deleteSoftById(accountSession.id).map(_ => None)
           } else {
             logger.info("Session is still valid, not deleting it")
             Some(accountSession).pure[ConnectionIO]
           }
         }).transact(transactor)
    } yield isExpired
  }

}
