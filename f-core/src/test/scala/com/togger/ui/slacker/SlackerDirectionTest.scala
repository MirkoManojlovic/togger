package com.togger.ui.slacker

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class SlackerDirectionTest extends AsyncJsWordSpec {

  private val first = LocalDate.of(2026, 10, 19)
  private val days = Seq(first, first.plusDays(1), first.plusDays(2))

  testNameFromClassName should {

    "walk from today into the past" in { _ =>
      SlackerDirection.TodayToPast.order(days) shouldBe days.reverse
    }

    "walk from the past up to today" in { _ =>
      SlackerDirection.PastToToday.order(days.reverse) shouldBe days
    }

    "have nothing to order for no days" in { _ =>
      SlackerDirection.TodayToPast.order(Seq.empty) shouldBe Seq.empty
    }

    "choose by the flag" in { _ =>
      SlackerDirection.of(pastFirst = true) shouldBe SlackerDirection.PastToToday
      SlackerDirection.of(pastFirst = false) shouldBe SlackerDirection.TodayToPast
    }
  }
}
