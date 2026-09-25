package com.togger.ui.auth

import com.togger.auth.AuthAccountFe
import com.togger.auth.UserRole.NoPermissionsUserRole
import com.togger.common.suites.{AsyncJsWordSpec, OwnerForTestingTrait, TestEvents}
import com.togger.dto.login.LoginResponseDto
import com.togger.ui.localstorage.LocalStorageTest
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.OptionValues

import java.util.UUID

class LoginRepoTest extends AsyncJsWordSpec with AsyncMockFactory with OwnerForTestingTrait with OptionValues {

  val accountId: UUID = UUID.randomUUID()
  val sessionId = "sessionId"
  val firstName = "Ada"
  val lastName = "Lovelace"
  val email = "ada@togger.com"
  val dto = LoginResponseDto(accountId, sessionId, firstName, lastName, email)

  val savedAccount: AuthAccountFe =
    AuthAccountFe(accountId, sessionId, NoPermissionsUserRole, Some(firstName), Some(lastName), Some(email))

  testNameFromClassName should {

    "save, delete and retrieve account" in { _ =>
      lazy val localStorage = new LocalStorageTest
      lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")
      lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)

      loginRepo.account shouldBe None
      loginRepo.save(dto)
      loginRepo.account.value shouldBe savedAccount
      loginRepo.delete()
      loginRepo.account shouldBe None
    }

    "retrieve an account stored before the name and email fields existed" in { _ =>
      lazy val localStorage = new LocalStorageTest
      lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")

      val accountWithoutNameAndEmail = AuthAccountFe(accountId, sessionId, NoPermissionsUserRole)

      val storedBeforeNameAndEmailExisted =
        accountWithoutNameAndEmail.asJson
          .mapObject(_.remove("firstName").remove("lastName").remove("email"))
          .noSpaces

      localStorage.setItem(tokenStorageKeys.accessTokenKey, storedBeforeNameAndEmailExisted)

      lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)

      loginRepo.account.value shouldBe accountWithoutNameAndEmail
    }

    "set and unset authenticated signal" in { owner =>
      lazy val localStorage = new LocalStorageTest
      lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")
      lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)

      val testEvents = TestEvents(loginRepo.authenticatedSignal, owner)

      runTasksAsyncFuture[Option[AuthAccountFe]](
        testEvents,
        DelayedTask(
          delay = 0,
          testEventsNew => {
            testEventsNew.printEvents()
            testEventsNew.events should have size 1
            testEventsNew.nTh(0) shouldBe None

            loginRepo.account shouldBe None
          }
        ),
        DelayedTask(
          delay = 0,
          testEventsNew => {
            loginRepo.save(dto)

            testEventsNew.printEvents()
            testEventsNew.events should have size 2
            testEventsNew.nTh(1).value shouldBe savedAccount

            loginRepo.account shouldBe Some(savedAccount)
          }
        ),
        DelayedTask(
          delay = 0,
          testEventsNew => {
            loginRepo.delete()

            testEventsNew.printEvents()
            testEventsNew.events should have size 3
            testEventsNew.nTh(2) shouldBe None

            loginRepo.account shouldBe None
          }
        )
      )

    }

  }

}
