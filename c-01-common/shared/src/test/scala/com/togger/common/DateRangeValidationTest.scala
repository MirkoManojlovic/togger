package com.togger.common

import com.togger.common.DateRangeValidation.{DateBeforeStartDate, WeekendDateNotAllowed}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

import java.time.{DayOfWeek, LocalDate}

class DateRangeValidationTest
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with TableDrivenPropertyChecks
    with NonEmptyChainHelpers {

  private val dateRangeValidation = new DateRangeValidation

  private val monFriWorkDays: Set[DayOfWeek] =
    Set(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

  // A project that works weekends instead of weekdays, used to prove the work-days set actually drives the rule.
  private val satSunWorkDays: Set[DayOfWeek] = Set(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

  // 2024-01-01 is a Monday
  private val monday = LocalDate.of(2024, 1, 1)
  private val saturday = LocalDate.of(2024, 1, 6)
  private val sunday = LocalDate.of(2024, 1, 7)
  private val nextMonday = LocalDate.of(2024, 1, 8)
  private val nextWednesday = LocalDate.of(2024, 1, 10)
  private val nextSaturday = LocalDate.of(2024, 1, 13)

  "DateRangeValidation.isWeekend" should {
    "identify Saturdays and Sundays as weekends for a Mon-Fri project" in {
      val weekendDays = Table("date", saturday, sunday)
      forAll(weekendDays)(date => dateRangeValidation.isWeekend(date, monFriWorkDays) shouldBe true)
    }

    "not treat week days as weekends for a Mon-Fri project" in {
      dateRangeValidation.isWeekend(monday, monFriWorkDays) shouldBe false
    }

    "treat a week day as a non-work day for a project configured to work weekends instead" in {
      dateRangeValidation.isWeekend(monday, satSunWorkDays) shouldBe true
    }

    "not treat Saturday as a non-work day for a project configured to work weekends" in {
      dateRangeValidation.isWeekend(saturday, satSunWorkDays) shouldBe false
    }
  }

  "DateRangeValidation.validate" should {
    "accept work days for all fields" in {
      dateRangeValidation.validate(Some(monday), Some(nextMonday), None, monFriWorkDays).value shouldBe (())
    }

    "accept missing (optional) fields" in {
      dateRangeValidation.validate(None, None, None, monFriWorkDays).value shouldBe (())
    }

    "reject a weekend start date" in {
      dateRangeValidation
        .validate(Some(saturday), Some(nextMonday), None, monFriWorkDays)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Start date", saturday)
    }

    "reject a weekend due date" in {
      dateRangeValidation
        .validate(Some(monday), Some(sunday), None, monFriWorkDays)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Due date", sunday)
    }

    "reject a weekend end date" in {
      dateRangeValidation
        .validate(Some(monday), Some(nextMonday), Some(saturday), monFriWorkDays)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("End date", saturday)
    }

    "accumulate errors for multiple weekend dates" in {
      dateRangeValidation
        .validate(Some(saturday), Some(sunday), Some(saturday), monFriWorkDays)
        .left
        .value
        .asList should contain theSameElementsAs Seq(
        WeekendDateNotAllowed("Start date", saturday),
        WeekendDateNotAllowed("Due date", sunday),
        WeekendDateNotAllowed("End date", saturday)
      )
    }

    "accept an due date equal to the start date" in {
      dateRangeValidation.validate(Some(monday), Some(monday), None, monFriWorkDays).value shouldBe (())
    }

    "accept an end date equal to the start date" in {
      dateRangeValidation.validate(Some(monday), Some(nextMonday), Some(monday), monFriWorkDays).value shouldBe (())
    }

    "reject an due date before the start date" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(monday), None, monFriWorkDays)
        .left
        .value
        .asList should contain only DateBeforeStartDate("Due date", monday, nextMonday)
    }

    "reject an end date before the start date" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(nextMonday), Some(monday), monFriWorkDays)
        .left
        .value
        .asList should contain only DateBeforeStartDate("End date", monday, nextMonday)
    }

    "accumulate weekend and before-start-date errors together" in {
      dateRangeValidation
        .validate(Some(nextMonday), Some(saturday), None, monFriWorkDays)
        .left
        .value
        .asList should contain theSameElementsAs Seq(
        WeekendDateNotAllowed("Due date", saturday),
        DateBeforeStartDate("Due date", saturday, nextMonday)
      )
    }

    "accept a Saturday date for a project configured to work weekends" in {
      dateRangeValidation.validate(Some(saturday), Some(nextSaturday), None, satSunWorkDays).value shouldBe (())
    }

    "reject a week day date for a project configured to work weekends" in {
      dateRangeValidation
        .validate(Some(saturday), Some(nextWednesday), None, satSunWorkDays)
        .left
        .value
        .asList should contain only WeekendDateNotAllowed("Due date", nextWednesday)
    }
  }

}
