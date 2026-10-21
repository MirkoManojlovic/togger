package com.togger.ui.contributions

import com.togger.routes.ContributionsParams
import com.togger.ui.milestone.{ActiveDateFilter, MonthOption}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDate

class ContributionsFilterMappingTest extends AnyWordSpec with Matchers {

  private def day(month: Int, dayOfMonth: Int) = LocalDate.of(2026, month, dayOfMonth)

  "ContributionsFilterMapping.monthOptions" should {
    "offer twelve months up to the one of today, the latest first, across the new year" in {
      val months = ContributionsFilterMapping.monthOptions(day(2, 10))
      months should have size 12
      months.head shouldBe MonthOption(2026, 2)
      months(1) shouldBe MonthOption(2026, 1)
      months(2) shouldBe MonthOption(2025, 12)
      months.last shouldBe MonthOption(2025, 3)
    }
  }

  "ContributionsFilterMapping.paramsOf" should {

    "ask for every day when no month is ticked" in {
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByMonth(Set.empty)) shouldBe ContributionsParams.default
    }

    "ask for a whole month, to its last day" in {
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByMonth(Set((2026, 2)))) shouldBe
        ContributionsParams(Some(day(2, 1)), Some(day(2, 28)))
    }

    "span months that are not next to each other" in {
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByMonth(Set((2026, 8), (2025, 12), (2026, 3)))) shouldBe
        ContributionsParams(Some(LocalDate.of(2025, 12, 1)), Some(day(8, 31)))
    }

    "take a range as it is" in {
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByRange(Some(day(10, 1)), None)) shouldBe
        ContributionsParams(from = Some(day(10, 1)))
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByRange(Some(day(10, 5)), Some(day(10, 9)))) shouldBe
        ContributionsParams(Some(day(10, 5)), Some(day(10, 9)))
    }

    "ask for every day from a range that ends before it starts" in {
      ContributionsFilterMapping.paramsOf(ActiveDateFilter.ByRange(Some(day(10, 9)), Some(day(10, 5)))) shouldBe
        ContributionsParams.default
    }
  }

  "ContributionsFilterMapping.withDefaultRange" should {

    "make a page without a range the last three months, up to today" in {
      ContributionsFilterMapping.withDefaultRange(ContributionsParams.default, day(10, 21)) shouldBe
        ContributionsParams(Some(day(7, 21)), Some(day(10, 21)))
    }

    "start on the last day of a shorter month when today's day number does not exist in it" in {
      ContributionsFilterMapping.withDefaultRange(ContributionsParams.default, day(5, 31)) shouldBe
        ContributionsParams(Some(day(2, 28)), Some(day(5, 31)))
    }

    "reach back over the new year" in {
      ContributionsFilterMapping.withDefaultRange(ContributionsParams.default, day(1, 15)) shouldBe
        ContributionsParams(Some(LocalDate.of(2025, 10, 15)), Some(day(1, 15)))
    }

    "keep the range of the address" in {
      val range = ContributionsParams(Some(day(3, 1)), None)
      ContributionsFilterMapping.withDefaultRange(range, day(10, 21)) shouldBe range
    }
  }
}
