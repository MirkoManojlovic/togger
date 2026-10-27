package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec

import java.time.{DayOfWeek, LocalDate}

class MilestoneSummaryPresetsTest extends AsyncJsWordSpec {

  private val monToFri = Set(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
  )

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

    "keep the week of a Sunday and of a Monday the same" in { _ =>
      val sunday = LocalDate.of(2026, 11, 1)
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneWeek, sunday) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneWeek, LocalDate.of(2026, 10, 26)) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "handle the length of February" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.OneMonth, LocalDate.of(2028, 2, 10)) shouldBe
        (LocalDate.of(2028, 2, 1) -> LocalDate.of(2028, 2, 29))
    }

    "see no misalignment when the first and last day are work days" in { _ =>
      // 2026-12-01 is a Tuesday and 2026-12-31 a Thursday
      MilestoneSummaryPresets.misalignedPresets(LocalDate.of(2026, 12, 9), monToFri) shouldBe Seq.empty
    }

    "see the month misaligned when it starts or ends on a weekend" in { _ =>
      // 2026-10-31 is a Saturday
      MilestoneSummaryPresets.misalignedPresets(wednesday, monToFri) shouldBe Seq(MilestoneSummaryPreset.OneMonth)
      // 2026-11-01 is a Sunday
      MilestoneSummaryPresets.misalignedPresets(LocalDate.of(2026, 11, 10), monToFri) shouldBe
        Seq(MilestoneSummaryPreset.OneMonth)
    }

    "see the week presets misaligned when the project does not work on Mondays" in { _ =>
      MilestoneSummaryPresets.misalignedPresets(LocalDate.of(2026, 12, 9), monToFri - DayOfWeek.MONDAY) shouldBe
        Seq(MilestoneSummaryPreset.OneWeek, MilestoneSummaryPreset.TwoWeeks)
    }

    "see no misalignment while the work days are not loaded" in { _ =>
      MilestoneSummaryPresets.misalignedPresets(wednesday, Set.empty) shouldBe Seq.empty
    }
  }
}
