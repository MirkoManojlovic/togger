package com.togger.ui.components

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class DatePickerInputTest extends AnyWordSpec with Matchers {

  "DatePickerInput.parse" should {
    "parse a valid yyyy-MM-dd date" in {
      DatePickerInput.parse("2026-10-02") shouldBe Some(LocalDate.of(2026, 10, 2))
    }

    "trim surrounding whitespace" in {
      DatePickerInput.parse("  2026-10-02  ") shouldBe Some(LocalDate.of(2026, 10, 2))
    }

    "return None for a blank string" in {
      DatePickerInput.parse("") shouldBe None
    }

    "return None for unparseable text" in {
      DatePickerInput.parse("tomorrow") shouldBe None
    }

    "return None for an invalid calendar date" in {
      DatePickerInput.parse("2026-02-30") shouldBe None
    }
  }
}
