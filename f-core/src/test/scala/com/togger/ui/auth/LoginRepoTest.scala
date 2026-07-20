package com.togger.ui.auth

import com.togger.auth.AuthAccountFe
import com.togger.auth.UserRole.NoPermissionsUserRole
import com.togger.common.suites.{AsyncJsWordSpec, OwnerForTestingTrait, TestEvents}
import com.togger.dto.login.LoginResponseDto
import com.togger.ui.localstorage.LocalStorageTest
import org.scalamock.scalatest.AsyncMockFactory
import org.scalatest.OptionValues

import java.util.UUID

class LoginRepoTest extends AsyncJsWordSpec with AsyncMockFactory with OwnerForTestingTrait with OptionValues {

  val accountId: UUID = UUID.randomUUID()
  val sessionId = "sessionId"
  val dto = LoginResponseDto(accountId, sessionId)

  testNameFromClassName should {

    "save, delete and retrieve account" in { _ =>
      lazy val localStorage = new LocalStorageTest
      lazy val tokenStorageKeys = TokenStorageKeys("accessTokenKey", "refreshTokenKey")
      lazy val loginRepo = new LoginRepo(localStorage, tokenStorageKeys)

      loginRepo.account shouldBe None
      loginRepo.save(dto)
      loginRepo.account.value shouldBe AuthAccountFe(accountId, sessionId, NoPermissionsUserRole)
      loginRepo.delete()
      loginRepo.account shouldBe None
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
            testEventsNew.nTh(1).value shouldBe AuthAccountFe(accountId, sessionId, NoPermissionsUserRole)

            loginRepo.account shouldBe Some(AuthAccountFe(accountId, sessionId, NoPermissionsUserRole))
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
