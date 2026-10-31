package com.togger.preview

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.togger.common.TgResponseTypes.TgRespIO
import com.togger.common.{TgClock, TgJsonFailure}
import com.togger.dto.login.LoginResponseDto
import com.togger.ui.auth.{LoginRepo, TokenStorageKeys}
import com.togger.ui.localstorage.LocalStorageTest

import java.time.{Instant, LocalDate, LocalTime, OffsetDateTime, ZoneOffset}
import scala.concurrent.Future

class FixedTestClock(instant: Instant = Instant.parse("2026-06-15T10:00:00Z")) extends TgClock {
  override def currentInstant: Instant = instant
  override def currentOffsetDateTime(zoneOffset: ZoneOffset): OffsetDateTime = instant.atOffset(zoneOffset)
  override def currentYear(zoneOffset: ZoneOffset): Int = currentOffsetDateTime(zoneOffset).getYear
  override def currentDate(zoneOffset: ZoneOffset): LocalDate = currentOffsetDateTime(zoneOffset).toLocalDate
  def midnight: LocalTime = LocalTime.MIDNIGHT
}

/** A seeded preview module on an in-memory storage, with helpers to act as a logged in account. */
class PreviewTestEnv {
  val storage = new LocalStorageTest
  val loginRepo = new LoginRepo(storage, TokenStorageKeys("accessTokenKey", "refreshTokenKey"))
  val clock = new FixedTestClock
  val module = new PreviewModule(storage, loginRepo, clock)
  module.ensureSeeded()

  def loginAs(accountId: java.util.UUID): Unit = {
    val account = module.accounts.accounts.find(accountId).get
    loginRepo.save(LoginResponseDto(account.id, "session", account.firstName, account.lastName, account.email))
  }

  def loginAsDemo(): Unit = loginAs(PreviewDemo.AccountId)

  def run[A](response: TgRespIO[A]): Future[Either[TgJsonFailure, A]] =
    response.map(_.map(_.body.get)).unsafeToFuture()

  def runEmpty[A](response: TgRespIO[A]): Future[Either[TgJsonFailure, Unit]] =
    response.map(_.map(_ => ())).unsafeToFuture()

  def runIO[A](io: IO[A]): Future[A] = io.unsafeToFuture()
}
