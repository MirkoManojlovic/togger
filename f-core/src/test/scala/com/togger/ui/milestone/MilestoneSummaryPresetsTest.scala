package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class MilestoneSummaryPresetsTest extends AsyncJsWordSpec {

  // 2026-10-28 is a Wednesday
  private val wednesday = LocalDate.of(2026, 10, 28)

  testNameFromClassName should {

    "make one week the Monday to Friday of the current week" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneWeek, wednesday) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "make two weeks the Monday of the current week to the Friday of the next" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.TwoWeeks, wednesday) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 11, 6))
    }

    "make one month the first to the last day of the current month" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneMonth, wednesday) shouldBe
        (LocalDate.of(2026, 10, 1) -> LocalDate.of(2026, 10, 31))
    }

    "keep the week of a Sunday with the Monday before it" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneWeek, LocalDate.of(2026, 11, 1)) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "keep the week of a Monday starting on that Monday" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneWeek, LocalDate.of(2026, 10, 26)) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "handle the length of February" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneMonth, LocalDate.of(2028, 2, 10)) shouldBe
        (LocalDate.of(2028, 2, 1) -> LocalDate.of(2028, 2, 29))
    }
  }
}
