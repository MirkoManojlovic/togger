package com.togger.account

import cats.effect.unsafe.implicits.global
import cats.implicits._
import com.togger.TestLoggingJvm
import com.togger.common._
import com.togger.db.MockedTransactor
import com.togger.dto.login.{DeviceInfo, ExpireSessionResult, LoginDto, LoginResponseDto}
import com.togger.login.SessionTokenFactory
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.time.ZoneOffset

class AccountServiceTest
    extends AsyncWordSpec
    with AsyncMockFactory
    with Matchers
    with MockedTransactor
    with DateHelpers
    with TimeHelpers
    with TestLoggingJvm
    with TgAccount {

  val accountRepo = mock[AccountRepoImpl]
  val accountSessionRepo = mock[AccountSessionRepo]
  val sessionTokenFactory = mock[SessionTokenFactory]
  val tgClock = mock[TgClock]

  val accountService = new AccountService(
    accountRepo = accountRepo,
    accountSessionRepo = accountSessionRepo,
    sessionTokenFactory = sessionTokenFactory,
    tgClock = tgClock,
    transactor = transactorMock
  )

  val account = createAccount(1)

  val accountSession = AccountSession(
    id = -1L,
    accountId = -1L,
    sessionToken = "sessionToken1",
    deviceInfo = "some device info",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  "AccountService" should {
    "create account" in {
      val insertedAccount = account.copy(id = 123L)
      (accountRepo.insert _).expects(account).returns(insertedAccount.pure[ConnectionIO])

      accountService
        .createAccount(account)
        .map(newAccount => newAccount shouldBe insertedAccount)
        .unsafeToFuture()
    }

    "login user when credentials are correct" in {

      val dto = LoginDto(
        email = account.email,
        password = passwordString,
        deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0")
      )

      val sessionToken = "new-session-token"

      val accountSession = AccountSession(
        id = -1,
        accountId = account.id,
        sessionToken = sessionToken,
        deviceInfo = dto.deviceInfo.toString,
        createdAt = nowAsInstant,
        updatedAt = nowAsInstant
      )

      (accountRepo.findByEmail _).expects(dto.email).returns(account.pure[ConnectionIO])
      (() => sessionTokenFactory.generateSessionToken).expects().returns(sessionToken)
      (() => tgClock.currentInstant).expects().returns(nowAsInstant).twice()
      (accountSessionRepo.insert _).expects(accountSession).returns(accountSession.pure[ConnectionIO])

      accountService
        .login(dto)
        .map(responseDto => responseDto shouldBe LoginResponseDto(accountId = account.id, sessionId = sessionToken))
        .unsafeToFuture()
    }

    "return an error when email is invalid" in {
      val dto = LoginDto(
        email = account.email,
        password = passwordString,
        deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0")
      )

      (accountRepo.findByEmail _).expects(dto.email).returns(UnexpectedEnd.raiseError[ConnectionIO, Account])

      recoverToSucceededIf[InvalidCredentials.type](
        accountService
          .login(dto)
          .unsafeToFuture()
      )
    }

    "return an error when password is invalid" in {
      val dto = LoginDto(
        email = account.email,
        password = passwordString + "a",
        deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0")
      )

      (accountRepo.findByEmail _).expects(dto.email).returns(account.pure[ConnectionIO])

      recoverToSucceededIf[InvalidCredentials.type](
        accountService
          .login(dto)
          .unsafeToFuture()
      )
    }

    "expire sessions older than the date" in {
      val deleted = 15
      val inDb = 45L
      (accountSessionRepo.deleteSoftByDate _).expects(nowAsInstant).returns(deleted.pure[ConnectionIO])
      (accountSessionRepo.countNotSoftDeleted _).expects().returns(inDb.pure[ConnectionIO])

      accountService
        .expireSessionsOlderThan(nowAsInstant)
        .map(result => result shouldBe ExpireSessionResult(deleted = deleted, inDb = inDb))
        .unsafeToFuture()
    }

    "log out everyone" in {
      val numberOfSessionsDeleted = 45342
      (accountSessionRepo.deleteAllSoft _).expects().returns(numberOfSessionsDeleted.pure[ConnectionIO])

      accountService.logoutEveryone.map(sessions => sessions shouldBe numberOfSessionsDeleted).unsafeToFuture()
    }

    "return account session by token" in {
      val token = "some-token"
      (accountSessionRepo.findByTokenNotSoftDeleted _).expects(token).returns(accountSession.pure[ConnectionIO])

      accountService.findByToken(token).map(session => session shouldBe accountSession).unsafeToFuture
    }

    "is session expired" should {

      val sessionToken = "expired_token"

      "return None when session is not found" in {
        val sessionToken = "expired_token"

        (accountSessionRepo.findByTokenNotSoftDeleted _)
          .expects(sessionToken)
          .returns(UnexpectedEnd.raiseError[ConnectionIO, AccountSession])

        accountService.isSessionExpired(sessionToken).map(result => result shouldBe None).unsafeToFuture()
      }

      "soft delete a session when session is older than limit and return None" in {
        val expiredAccountSession =
          accountSession.copy(createdAt = today.atTime(now).minusHours(12).minusMinutes(1).toInstant(ZoneOffset.UTC))

        (accountSessionRepo.findByTokenNotSoftDeleted _)
          .expects(sessionToken)
          .returns(expiredAccountSession.pure[ConnectionIO])
        (tgClock.currentInstant _).expects().returns(nowAsInstant)
        (accountSessionRepo.deleteSoftById _).expects(expiredAccountSession.id).returns(1.pure[ConnectionIO])

        accountService.isSessionExpired(sessionToken).map(result => result shouldBe None).unsafeToFuture()
      }

      "update session and return account sessions when session is not older than the limit" in {
        val nonExpiredAccountSession = accountSession.copy(createdAt = today.atTime(now).toInstant(ZoneOffset.UTC))

        (accountSessionRepo.findByTokenNotSoftDeleted _)
          .expects(sessionToken)
          .returns(nonExpiredAccountSession.pure[ConnectionIO])
        (tgClock.currentInstant _).expects().returns(nowAsInstant)

        accountService
          .isSessionExpired(sessionToken)
          .map(result => result shouldBe Some(nonExpiredAccountSession))
          .unsafeToFuture()
      }

    }

  }
}
