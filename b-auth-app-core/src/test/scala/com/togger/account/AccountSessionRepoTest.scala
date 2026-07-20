package com.togger.account

import cats.implicits._
import com.togger.common.{DateHelpers, TgIds, TimeHelpers}
import com.togger.dto.login.DeviceInfo
import doobie.implicits._
import doobie.postgres.implicits._

import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class AccountSessionRepoTest extends AccountTgDbSpec with TimeHelpers with DateHelpers with TgAccount {

  val accountRepo = new AccountRepoImpl
  val accountSessionRepo = new AccountSessionRepo

  val accountSession = AccountSession(
    id = UUID.randomUUID(),
    accountId = TgIds.UNASSIGNED_UUID,
    sessionToken = "sessionToken1",
    deviceInfo = "some device info",
    createdAt = nowAsInstant,
    updatedAt = nowAsInstant
  )

  testNameFromClassName should {

    "do basic crud operations" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        foundSession1 <- accountSessionRepo.findById(savedSession.id)
        newUpdatedAt = nowAsInstant.plus(1, ChronoUnit.DAYS)
        newSessionToken = "newSessionToken"
        updatedSession <- accountSessionRepo.updateSessionToken(
          id = savedSession.id,
          newSessionToken = newSessionToken,
          updatedAt = newUpdatedAt
        )
        foundSession2 <- accountSessionRepo.findById(savedSession.id)
        _ <- accountSessionRepo.deleteById(savedSession.id)
        foundSession3 <- accountSessionRepo.findById(savedSession.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedSession shouldBe foundSession1)
        cp(updatedSession shouldBe foundSession2)
        cp.reportAll()
        foundSession3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "do basic crud operations - SOFT DELETE" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        savedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        foundSession1 <- accountSessionRepo.findByIdNotSoftDeleted(savedSession.id)
        newUpdatedAt = nowAsInstant.plus(1, ChronoUnit.DAYS)
        newSessionToken = "newSessionToken"
        updatedSession <- accountSessionRepo.updateSessionToken(
          id = savedSession.id,
          newSessionToken = newSessionToken,
          updatedAt = newUpdatedAt
        )
        foundSession2 <- accountSessionRepo.findByIdNotSoftDeleted(savedSession.id)
        _ <- accountSessionRepo.deleteSoftById(savedSession.id)
        foundSession3 <- accountSessionRepo.findByIdNotSoftDeleted(savedSession.id).attempt
      } yield {
        val cp = new Checkpoint
        cp(savedSession shouldBe foundSession1)
        cp(updatedSession shouldBe foundSession2)
        cp.reportAll()
        foundSession3.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "should delete all sessions older than" in { transactor =>
      val dates = List(
        previousMonday.atTime(at10),
        previousThursday.atTime(at17),
        previousFriday.atTime(at1),
        yesterday.atTime(at14),
        today.atTime(at22),
        tomorrow.atTime(at20),
        dayAfterTomorrow.atTime(at12),
        nextMonday.atTime(at9)
      )

      val sessions = dates.zipWithIndex.map { case (dateAsInstant, index) =>
        AccountSession(
          id = UUID.randomUUID(),
          accountId = TgIds.UNASSIGNED_UUID,
          sessionToken = s"sessionToken-$index",
          deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0").toString,
          createdAt = dateAsInstant.toInstant(ZoneOffset.UTC),
          updatedAt = dateAsInstant.toInstant(ZoneOffset.UTC)
        )
      }

      val res = for {
        savedAccount <- createAccount
        _ <- sessions.map(session => accountSessionRepo.insert(session.copy(accountId = savedAccount.id))).sequence
        numberOfSessionsDeleted <- accountSessionRepo.deleteByDate(yesterday.atTime(at14).toInstant(ZoneOffset.UTC))
        totalNumberOfSessions <- accountSessionRepo.count()
      } yield {
        val cp = new Checkpoint
        cp(numberOfSessionsDeleted shouldBe 3)
        cp.reportAll()

        totalNumberOfSessions shouldBe 5
      }
      res.transact(transactor)
    }

    "should delete all sessions older than - SOFT DELETE" in { transactor =>
      val dates = List(
        previousMonday.atTime(at10),
        previousThursday.atTime(at17),
        previousFriday.atTime(at1),
        yesterday.atTime(at14),
        tomorrow.atTime(at20),
        dayAfterTomorrow.atTime(at12),
        nextMonday.atTime(at9)
      )

      val sessions = dates.zipWithIndex.map { case (dateAsInstant, index) =>
        AccountSession(
          id = UUID.randomUUID(),
          accountId = TgIds.UNASSIGNED_UUID,
          sessionToken = s"sessionToken-$index",
          deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0").toString,
          createdAt = dateAsInstant.toInstant(ZoneOffset.UTC),
          updatedAt = dateAsInstant.toInstant(ZoneOffset.UTC)
        )
      }

      val res = for {
        savedAccount <- createAccount
        _ <- sessions.map(session => accountSessionRepo.insert(session.copy(accountId = savedAccount.id))).sequence
        numberOfSessionsDeleted <- accountSessionRepo.deleteSoftByDate(yesterday.atTime(at14).toInstant(ZoneOffset.UTC))
        totalNumberOfSessions <- accountSessionRepo.countNotSoftDeleted()
      } yield {
        val cp = new Checkpoint
        cp(numberOfSessionsDeleted shouldBe 3)
        cp.reportAll()

        totalNumberOfSessions shouldBe 4
      }
      res.transact(transactor)
    }

    "should delete all sessions" in { transactor =>
      val sessions = (1 to 100)
        .map(index =>
          AccountSession(
            id = UUID.randomUUID(),
            accountId = TgIds.UNASSIGNED_UUID,
            sessionToken = s"sessionToken-$index",
            deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0").toString,
            createdAt = today.plusDays(index).atTime(at14).toInstant(ZoneOffset.UTC),
            updatedAt = today.plusDays(index).atTime(at14).toInstant(ZoneOffset.UTC)
          )
        )
        .toList

      val res = for {
        savedAccount <- createAccount
        _ <- sessions.map(session => accountSessionRepo.insert(session.copy(accountId = savedAccount.id))).sequence
        countBefore <- accountSessionRepo.count()
        numberOfDeletedSessions <- accountSessionRepo.deleteAll()
        countAfter <- accountSessionRepo.count()
      } yield {
        val cp = new Checkpoint
        cp(countBefore should not be 0)
        cp(countAfter shouldBe 0)
        cp(countBefore shouldBe numberOfDeletedSessions)
        countBefore should not be countAfter
      }

      res.transact(transactor).unsafeToFuture
    }

    "should delete all sessions - SOFT DELETE" in { transactor =>
      val sessions = (1 to 100)
        .map(index =>
          AccountSession(
            id = UUID.randomUUID(),
            accountId = TgIds.UNASSIGNED_UUID,
            sessionToken = s"sessionToken-$index",
            deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0").toString,
            createdAt = today.plusDays(index).atTime(at14).toInstant(ZoneOffset.UTC),
            updatedAt = today.plusDays(index).atTime(at14).toInstant(ZoneOffset.UTC)
          )
        )
        .toList

      val res = for {
        savedAccount <- createAccount
        _ <- sessions.map(session => accountSessionRepo.insert(session.copy(accountId = savedAccount.id))).sequence
        countBefore <- accountSessionRepo.countNotSoftDeleted()
        numberOfDeletedSessions <- accountSessionRepo.deleteAllSoft()
        countAfter <- accountSessionRepo.countNotSoftDeleted()
      } yield {
        val cp = new Checkpoint
        cp(countBefore should not be 0)
        cp(countAfter shouldBe 0)
        cp(countBefore shouldBe numberOfDeletedSessions)
        countBefore should not be countAfter
      }

      res.transact(transactor).unsafeToFuture
    }

    "should find session by token" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        insertedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        foundSession <- accountSessionRepo.findByToken(accountSession.sessionToken)
      } yield {
        insertedSession shouldBe foundSession
      }

      res.transact(transactor)
    }

    "should find session by token - SOFT DELETE" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        insertedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        foundSession <- accountSessionRepo.findByTokenNotSoftDeleted(insertedSession.sessionToken)
        _ <- accountSessionRepo.deleteSoftById(insertedSession.id)
        notFoundSession <- accountSessionRepo.findByTokenNotSoftDeleted(insertedSession.sessionToken).attempt
      } yield {
        val cp = new Checkpoint
        cp(insertedSession shouldBe foundSession)
        notFoundSession.isLeft shouldBe true
      }

      res.transact(transactor)
    }

    "should return account session with account" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        insertedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        accountAndSession <- accountSessionRepo.findByTokenWithAccount(insertedSession.sessionToken)
      } yield {
        val cp = new Checkpoint
        cp(accountAndSession._1 shouldBe insertedSession)
        accountAndSession._2 shouldBe savedAccount
      }

      res.transact(transactor)
    }

    "should return account session with account - SOFT DELETE" in { transactor =>
      val res = for {
        savedAccount <- createAccount
        insertedSession <- accountSessionRepo.insert(accountSession.copy(accountId = savedAccount.id))
        accountAndSession <- accountSessionRepo.findByTokenWithAccountNotSoftDeleted(insertedSession.sessionToken)
        _ <- accountSessionRepo.deleteSoftById(insertedSession.id)
        accountAndSessionAfterDelete <- accountSessionRepo
          .findByTokenWithAccountNotSoftDeleted(insertedSession.sessionToken)
          .attempt
      } yield {
        val cp = new Checkpoint
        cp(accountAndSession._1 shouldBe insertedSession)
        cp(accountAndSession._2 shouldBe savedAccount)
        accountAndSessionAfterDelete.isLeft shouldBe true
      }

      res.transact(transactor)
    }
  }
}
