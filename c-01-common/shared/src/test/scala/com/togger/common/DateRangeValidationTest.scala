package com.togger.common

import com.togger.common.DateRangeValidation.{DateBeforeStartDate, WeekendDateNotAllowed}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class DateRangeValidationTest
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with TableDrivenPropertyChecks
    with NonEmptyChainHelpers {

  private val dateRangeValidation = new DateRangeValidation

  // 2024-01-01 is a Monday
  private val monday = LocalDate.of(2024, 1, 1)
  private val saturday = LocalDate.of(2024, 1, 6)
  private val sunday = LocalDate.of(2024, 1, 7)
  private val nextMonday = LocalDate.of(2024, 1, 8)

  "DateRangeValidation.isWeekend" should {
    "identify Saturdays and Sundays as weekends" in {
      val weekendDays = Table("date", saturday, sunday)
      forAll(weekendDays)(date => dateRangeValidation.isWeekend(date) shouldBe true)
    }

    "not treat week days as weekends" in {
      dateRangeValidation.isWeekend(monday) shouldBe false
    }
  }

  "DateRangeValidation.validate" should {
    "accept work days for all fields" in {
      dateRangeValidation.validate(Some(monday), Some(nextMonday), None).value shouldBe (())
    }

    "accept missing (optional) fields" in {
      dateRangeValidation.validate(None, None, None).value shouldBe (())
    }

    "reject a weekend start date" in {
      dateRangeValidation
        .validate(Some(saturday), Some(nextMonday), None)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Start date", saturday)
    }

    "reject a weekend estimated end date" in {
      dateRangeValidation
        .validate(Some(monday), Some(sunday), None)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Estimated end date", sunday)
    }

    "reject a weekend end date" in {
      dateRangeValidation
        .validate(Some(monday), Some(nextMonday), Some(saturday))
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("End date", saturday)
    }

    "accumulate errors for multiple weekend dates" in {
      dateRangeValidation
        .validate(Some(saturday), Some(sunday), Some(saturday))
        .left
        .value
        .asList should contain theSameElementsAs Seq(
        WeekendDateNotAllowed("Start date", saturday),
        WeekendDateNotAllowed("Estimated end date", sunday),
        WeekendDateNotAllowed("End date", saturday)
      )
    }

    "accept an estimated end date equal to the start date" in {
      dateRangeValidation.validate(Some(monday), Some(monday), None).value shouldBe (())
    }

    "accept an end date equal to the start date" in {
      dateRangeValidation.validate(Some(monday), Some(nextMonday), Some(monday)).value shouldBe (())
    }

    "reject an estimated end date before the start date" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(monday), None)
        .left
        .value
        .asList should contain only DateBeforeStartDate("Estimated end date", monday, nextMonday)
    }

    "reject an end date before the start date" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(nextMonday), Some(monday))
        .left
        .value
        .asList should contain only DateBeforeStartDate("End date", monday, nextMonday)
    }

    "accumulate weekend and before-start-date errors together" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(saturday), None)
        .left
        .value
        .asList should contain theSameElementsAs Seq(
        WeekendDateNotAllowed("Estimated end date", saturday),
        DateBeforeStartDate("Estimated end date", saturday, nextMonday)
      )
    }
  }

}
