package com.togger.auth

import com.togger.TgWordSpecJvm
import com.togger.auth.AccountInfoError.{BlankField, FieldTooLong, InvalidEmail}
import com.togger.dto.login.AccountInfoDto

class AccountInfoValidationTest extends TgWordSpecJvm {

  private val valid = AccountInfoDto("Ada", "Lovelace", "ada@togger.com")

  "AccountInfoValidation" should {

    "accept a valid dto" in {
      AccountInfoValidation.validate(valid) shouldBe Nil
    }

    "reject blank names" in {
      AccountInfoValidation.validate(valid.copy(firstName = " ", lastName = "")) shouldBe
        List(BlankField("First name"), BlankField("Last name"))
    }

    "reject names longer than the column allows" in {
      val tooLong = "a" * 51
      AccountInfoValidation.validate(valid.copy(firstName = tooLong)) shouldBe List(FieldTooLong("First name", 50))
    }

    "reject an email without an at sign" in {
      AccountInfoValidation.validate(valid.copy(email = "ada.togger.com")) shouldBe List(InvalidEmail)
    }
  }
}
