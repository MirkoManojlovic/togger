package com.togger.login

import cats.effect.IO
import cats.implicits._
import com.togger.account._
import com.togger.common._
import com.togger.dep.AuthDependenciesModule
import com.togger.dto.login._
import com.togger.http.HttpModule
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.http4s.circe.{jsonEncoder, jsonOf}
import org.http4s.client.Client
import org.http4s.implicits.http4sLiteralsSyntax
import org.http4s.{EntityDecoder, Method, Request}

import java.time.ZoneOffset

class LoginControllerTest extends AccountTgDbSpec with TgAccount with TimeHelpers with DateHelpers {

  val httpModule = new HttpModule()

  private def createHttpApp(transactor: Transactor[IO]) = {
    val authDependenciesModule = new AuthDependenciesModule(transactor)
    val httpModule = new HttpModule()

    import authDependenciesModule._

    val httpApp = httpModule.createHttpApp(
      List(
        loginController.login,
        loginController.expireOldSessions,
        loginController.logoutEveryone
      )
    )

    (httpApp, authDependenciesModule)
  }

  testNameFromClassName should {

    "login, user and create a session" in { trn =>
      val (httpApp, authDeps) = createHttpApp(trn)
      import authDeps._
      val client = Client.fromHttpApp(httpApp)

      val req = Request[IO](
        method = Method.POST,
        uri = uri"account/login"
      ).withEntity(
        LoginDto(
          email = "john@email.com",
          password = "A!good_1_password_is_good",
          deviceInfo = DeviceInfo(browser = "firefox", ipAddress = "0.0.0.0")
        ).asJson
      )

      implicit val entityDecoder: EntityDecoder[IO, TgJsonSuccess[LoginResponseDto]] = jsonOf

//      val saveAccount = for {
//        _ <- dropAndCreateAccountTypes(drops).transact(transactor)
//        _ <- accountService.createAccount(account)
//      } yield ()

      val res = for {
        _ <- createAccount.transact(trn)
        success <- client.expect[TgJsonSuccess[LoginResponseDto]](req)
        session <- accountService.findByToken(success.body.get.sessionId)
      } yield {
        logger.info(success.toString)
        val cp = new Checkpoint
        cp(session.sessionToken shouldBe success.body.get.sessionId)
        cp.reportAll()
        success.body.get.sessionId should not be empty
      }

      res.unsafeToFuture()
    }

    "should logout everyone" in { trn =>
      val (httpApp, authDeps) = createHttpApp(trn)
      import authDeps._
      val client = Client.fromHttpApp(httpApp)

      val loginDtos = (1 to 10).map(index =>
        LoginDto(
          email = "john@email.com",
          password = "A!good_1_password_is_good",
          deviceInfo = DeviceInfo(browser = s"firefox-$index", ipAddress = "0.0.0.0")
        )
      )

      val req = Request[IO](
        method = Method.GET,
        uri = uri"account/log-out-everyone/mirule123"
      )

      implicit val entityDecoder: EntityDecoder[IO, TgJsonSuccess[LogoutEveryoneDto]] = jsonOf

      val loginUsers = for {
        _ <- createAccount.transact(trn)
        repsonseDtos <- loginDtos.map(loginDto => accountService.login(loginDto)).toList.sequence
      } yield repsonseDtos

      val res = for {
        responseDtos <- loginUsers
        success <- client.expect[TgJsonSuccess[LogoutEveryoneDto]](req)
        res <- responseDtos
          .map(sessionToken => accountService.findByToken(sessionToken.sessionId))
          .sequence
          .handleError(e => Seq.empty)
      } yield {
        logger.info(success.toString)
        logger.info(s"Result is $res")
        res shouldBe empty
      }

      res.unsafeToFuture()
    }

    "should return error when trying to logout everyone when password is wrong" in { trn =>
      val (httpApp, authDeps) = createHttpApp(trn)
      import authDeps._
      val client = Client.fromHttpApp(httpApp)
      val loginDtos = (1 to 10).map(index =>
        LoginDto(
          email = "john@email.com",
          password = "A!good_1_password_is_good",
          deviceInfo = DeviceInfo(browser = s"firefox-$index", ipAddress = "0.0.0.0")
        )
      )

      val req = Request[IO](
        method = Method.GET,
        uri = uri"account/log-out-everyone/wrong-password"
      )

      implicit val entityDecoder: EntityDecoder[IO, BadRequest] = jsonOf

      val loginUsers = for {
        _ <- createAccount.transact(trn)
        repsonseDtos <- loginDtos.map(loginDto => accountService.login(loginDto)).toList.sequence
      } yield repsonseDtos

      val res = for {
        _ <- loginUsers
        error <- client.fetchAs[BadRequest](req)
      } yield {
        logger.info(s"Error is $error")
        error shouldBe TgJsonFailure.badRequest(TgJsonError("Invalid password", `type` = "String"))
      }

      res.unsafeToFuture()
    }

    "should expire older sessions" in { trn =>
      val (httpApp, authDeps) = createHttpApp(trn)
      import authDeps._
      val client = Client.fromHttpApp(httpApp)

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
          id = index,
          accountId = index,
          sessionToken = s"sessionToken-$index",
          deviceInfo = DeviceInfo(browser = "Firefox", ipAddress = "0.0.0.0").toString,
          createdAt = dateAsInstant.toInstant(ZoneOffset.UTC),
          updatedAt = dateAsInstant.toInstant(ZoneOffset.UTC)
        )
      }

      val req = Request[IO](
        method = Method.GET,
        uri = uri"account/expire-sessions"
      )

      implicit val entityDecoder: EntityDecoder[IO, TgJsonSuccess[ExpireSessionResult]] = jsonOf

      val saveAccount = for {
        savedAccount <- createAccount.transact(trn)
        sessions <- sessions
          .map(session => accountSessionRepo.insert(session.copy(accountId = savedAccount.id)))
          .sequence
          .transact(trn)
      } yield sessions

      for {
        _ <- saveAccount
        success <- client.expect[TgJsonSuccess[ExpireSessionResult]](req)
      } yield {
        logger.info(success.toString)
        success.body.get shouldBe ExpireSessionResult(deleted = 4, inDb = 3)
      }
    }

  }
}
