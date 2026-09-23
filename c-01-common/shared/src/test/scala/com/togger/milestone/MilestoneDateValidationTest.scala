package com.togger.milestone

import com.togger.common.NonEmptyChainHelpers
import com.togger.milestone.MilestoneDateValidation.WeekendDateNotAllowed
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

import java.time.LocalDate

class MilestoneDateValidationTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with TableDrivenPropertyChecks
    with NonEmptyChainHelpers {

  private val milestoneDateValidation = new MilestoneDateValidation

  // 2024-01-01 is a Monday
  private val monday = LocalDate.of(2024, 1, 1)
  private val saturday = LocalDate.of(2024, 1, 6)
  private val sunday = LocalDate.of(2024, 1, 7)
  private val nextMonday = LocalDate.of(2024, 1, 8)

  describe("MilestoneDateValidation.isWeekend") {
    it("should identify Saturdays and Sundays as weekends") {
      val weekendDays = Table("date", saturday, sunday)
      forAll(weekendDays)(date => milestoneDateValidation.isWeekend(date) shouldBe true)
    }

    it("should not treat week days as weekends") {
      milestoneDateValidation.isWeekend(monday) shouldBe false
    }
  }

  describe("MilestoneDateValidation.validate") {
    it("should accept work days for all fields") {
      milestoneDateValidation.validate(Some(monday), Some(nextMonday), None).value shouldBe (())
    }

    it("should accept missing (optional) fields") {
      milestoneDateValidation.validate(None, None, None).value shouldBe (())
    }

    it("should reject a weekend start date") {
      milestoneDateValidation
        .validate(Some(saturday), Some(nextMonday), None)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Start date", saturday)
    }

    it("should reject a weekend estimated end date") {
      milestoneDateValidation
        .validate(Some(monday), Some(sunday), None)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Estimated end date", sunday)
    }

    it("should reject a weekend end date") {
      milestoneDateValidation
        .validate(Some(monday), Some(nextMonday), Some(saturday))
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("End date", saturday)
    }

    it("should accumulate errors for multiple weekend dates") {
      milestoneDateValidation
        .validate(Some(saturday), Some(sunday), Some(saturday))
        .left
        .value
        .asList should contain theSameElementsAs Seq(
        WeekendDateNotAllowed("Start date", saturday),
        WeekendDateNotAllowed("Estimated end date", sunday),
        WeekendDateNotAllowed("End date", saturday)
      )
    }
  }

}
