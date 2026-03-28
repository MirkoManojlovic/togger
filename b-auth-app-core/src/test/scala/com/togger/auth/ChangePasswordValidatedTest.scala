package com.togger.auth

import com.togger.TgWordSpecJvm
import com.togger.auth.ChangePasswordError.PasswordsMustMatch
import com.togger.common.Password.{DoesNotContainDigit, DoesNotContainSpecialChar}
import com.togger.common.{NonEmptyChainHelpers, Password}
import com.togger.dto.login.ChangePasswordDto
import org.scalatest.EitherValues

// TODO 2: create a trait for validation tests EitherValues, NonEmptyChainHelpers...
class ChangePasswordValidatedTest extends TgWordSpecJvm with EitherValues with NonEmptyChainHelpers {

  val accountId = 1L
  val anotherValidPassword = Password.fromString("Password123789##").toOption.get
  val invalidPassword = "invalidPasswordBro"
  val validPassword: Password = Password.fromString("Password123456##").toOption.get

  "ChangePasswordValidated" should {

    "return an error when old password is not a valid password" in {
      val dto = ChangePasswordDto(accountId, invalidPassword, validPassword.value, validPassword.value)

      val result = ChangePasswordValidated.fromDto(dto)

      logger.info(result.toString)
      result.left.value.asList should contain theSameElementsAs Seq(
        DoesNotContainSpecialChar(invalidPassword),
        DoesNotContainDigit(invalidPassword)
      )
    }

    "return an error when passwords do not match" in {
      val dto = ChangePasswordDto(accountId, validPassword.value, validPassword.value, anotherValidPassword.value)

      val result = ChangePasswordValidated.fromDto(dto)

      logger.info(result.toString)
      result.left.value.asList should contain only PasswordsMustMatch
    }

    "return an error when new password is not a valid password" in {
      val dto = ChangePasswordDto(accountId, validPassword.value, invalidPassword, invalidPassword)

      val result = ChangePasswordValidated.fromDto(dto)

      logger.info(result.toString)
      result.left.value.asList should contain theSameElementsAs Seq(
        DoesNotContainSpecialChar(invalidPassword),
        DoesNotContainSpecialChar(invalidPassword),
        DoesNotContainDigit(invalidPassword),
        DoesNotContainDigit(invalidPassword)
      )
    }

  }

}
