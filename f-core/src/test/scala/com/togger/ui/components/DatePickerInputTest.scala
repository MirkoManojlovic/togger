package com.togger.ui.components

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class DatePickerInputTest extends AnyWordSpec with Matchers {

  private val today = LocalDate.of(2026, 10, 2)

  "DatePickerInput.parse" should {
    "parse a valid yyyy-MM-dd date" in {
      DatePickerInput.parse("2026-10-02", today) shouldBe Some(LocalDate.of(2026, 10, 2))
    }

    "trim surrounding whitespace" in {
      DatePickerInput.parse("  2026-10-02  ", today) shouldBe Some(LocalDate.of(2026, 10, 2))
    }

    "return None for a blank string" in {
      DatePickerInput.parse("", today) shouldBe None
    }

    "return None for unparseable text" in {
      DatePickerInput.parse("tomorrow", today) shouldBe None
    }

    "return None for an invalid calendar date" in {
      DatePickerInput.parse("2026-02-30", today) shouldBe None
    }

    "parse a bare two-digit day as that day in today's month and year" in {
      DatePickerInput.parse("12", today) shouldBe Some(LocalDate.of(2026, 10, 12))
    }

    "parse a bare one-digit day as that day in today's month and year" in {
      DatePickerInput.parse("5", today) shouldBe Some(LocalDate.of(2026, 10, 5))
    }

    "accept a leading-zero day" in {
      DatePickerInput.parse("02", today) shouldBe Some(LocalDate.of(2026, 10, 2))
    }

    "return None for a bare day that doesn't exist in today's month" in {
      DatePickerInput.parse("31", LocalDate.of(2026, 11, 1)) shouldBe None
    }

    "return None for a bare day of zero" in {
      DatePickerInput.parse("0", today) shouldBe None
    }

    "not treat a three-digit number as a bare day" in {
      DatePickerInput.parse("123", today) shouldBe None
    }
  }
}
