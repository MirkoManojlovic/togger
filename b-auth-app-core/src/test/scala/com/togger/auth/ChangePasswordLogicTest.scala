package com.togger.auth

import cats.implicits._
import com.togger.TgAsyncWordSpecMockJvm
import com.togger.account.{Account, AccountRepoImpl, HashedPasswordImpl, TgAccount}
import com.togger.auth.ChangePasswordError.{InvalidAccountId, PasswordsMustMatch}
import com.togger.common.Password.{DoesNotContainDigit, DoesNotContainSpecialChar}
import com.togger.common._
import com.togger.db.MockedTransactor
import com.togger.dto.login.ChangePasswordDto
import doobie.ConnectionIO
import doobie.implicits._
import doobie.util.invariant.UnexpectedEnd
import org.scalatest.EitherValues

import java.util.UUID

class ChangePasswordLogicTest
    extends TgAsyncWordSpecMockJvm
    with MockedTransactor
    with EitherValues
    with TgAccount
    with TimeHelpers {

  val accountId: UUID = UUID.randomUUID()
  val accountRepo = mock[AccountRepoImpl]
  val passwordUpdate = mock[PasswordUpdate]
  val tgClock = mock[TgClock]

  val account = createAccount(TgIds.UNASSIGNED_ACCOUNT_ID)

  val changePasswordLogic = new ChangePasswordLogic(
    accountRepo = accountRepo,
    passwordUpdate = passwordUpdate,
    tgClock = tgClock,
    transactor = transactorMock
  )

  val validPassword: Password = Password.fromString("A!good_1_password_is_good").toOption.get
  val anotherValidPassword: Password = Password.fromString("Password123789##").toOption.get
  val invalidPassword: String = "invalidPasswordBro"

  testNameFromClassName should {

    "return an error when passwords do not match" in {
      val dto = ChangePasswordDto(
        accountId = accountId,
        oldPassword = validPassword.value,
        newPassword = validPassword.value,
        confirmNewPassword = anotherValidPassword.value
      )

      val res = changePasswordLogic.change(dto)

      res.asserting(
        _.left.value shouldBe TgJsonFailure.badRequest(TgJsonError.fromError(PasswordsMustMatch))
      )
    }

    "return an error when old password is in invalid format" in {
      val dto = ChangePasswordDto(accountId, invalidPassword, validPassword.value, validPassword.value)
      val result = changePasswordLogic.change(dto)

      result.asserting(
        _.left.value shouldBe TgJsonFailure.badRequest(
          TgJsonError.fromErrorsSet(
            Set(DoesNotContainSpecialChar(invalidPassword), DoesNotContainDigit(invalidPassword))
          )
        )
      )
    }

    "return an error when new password is not a valid password" in {
      val dto = ChangePasswordDto(accountId, validPassword.value, invalidPassword, invalidPassword)
      val result = changePasswordLogic.change(dto)

      result.asserting(
        _.left.value shouldBe TgJsonFailure.badRequest(
          TgJsonError.fromErrorsSet(
            Set(
              DoesNotContainSpecialChar(invalidPassword),
              DoesNotContainSpecialChar(invalidPassword),
              DoesNotContainDigit(invalidPassword),
              DoesNotContainDigit(invalidPassword)
            )
          )
        )
      )
    }

    "return an error when old password is not in the db" in {
      val dto =
        ChangePasswordDto(accountId, validPassword.value, anotherValidPassword.value, anotherValidPassword.value)
      (accountRepo.findById _).expects(accountId).returns(UnexpectedEnd.raiseError[ConnectionIO, Account])

      val result = changePasswordLogic.change(dto)

      result.map(r => {
        logger.info(r.toString)
        r.left.value shouldBe TgJsonFailure.badRequest(TgJsonError.fromError(InvalidAccountId))
      })
    }

    "return 200 when changed password successfully" in {
      val dto =
        ChangePasswordDto(accountId, validPassword.value, anotherValidPassword.value, anotherValidPassword.value)
      (accountRepo.findById _).expects(accountId).returns(account.pure[ConnectionIO])
      val updatedCustomerAccount = account.copy(password = HashedPasswordImpl.fromPassword(anotherValidPassword))
      (passwordUpdate.run _).expects(account, anotherValidPassword).returns(updatedCustomerAccount)
      (tgClock.currentInstant _).expects().returns(nowAsInstant).twice()
      (accountRepo.update _)
        .expects(updatedCustomerAccount, tgClock.currentInstant)
        .returns(updatedCustomerAccount.pure[ConnectionIO])

      val result = changePasswordLogic.change(dto)

      result.map(r => {
        logger.info(r.toString)
        r.value shouldBe TgJsonSuccess.justMessage("Password successfully changed")
      })
    }

  }

}
