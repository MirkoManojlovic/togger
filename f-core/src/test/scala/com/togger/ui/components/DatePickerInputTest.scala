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

  "DatePickerSuggestions.all" should {
    // `today` (2026-10-02) is a Friday
    def resolve(label: String): LocalDate =
      DatePickerSuggestions.all.find(_.label == label).map(_.resolve(today)).get

    "resolve a plain weekday to that day in today's own week, even if already past" in {
      resolve("Monday") shouldBe LocalDate.of(2026, 9, 28)
    }

    "resolve a plain weekday to today itself when today is that weekday" in {
      resolve("Friday") shouldBe today
    }

    "resolve \"Next <weekday>\" to that weekday in the following week, distinct from the plain weekday" in {
      resolve("Next Monday") shouldBe LocalDate.of(2026, 10, 5)
      resolve("Next Monday") should not be resolve("Monday")
    }

    "resolve \"Previous <weekday>\" to that weekday in the prior week" in {
      resolve("Previous Monday") shouldBe LocalDate.of(2026, 9, 21)
    }
  }
}
