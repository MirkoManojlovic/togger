package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class MilestoneSummaryPresetsTest extends AsyncJsWordSpec {

  // 2026-10-28 is a Wednesday
  private val wednesday = LocalDate.of(2026, 10, 28)

  testNameFromClassName should {

    "make the current week the Monday to Friday of the current week" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.CurrentWeek, wednesday) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "keep the current week of a Sunday with the Monday before it" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.CurrentWeek, LocalDate.of(2026, 11, 1)) shouldBe
        (LocalDate.of(2026, 10, 26) -> LocalDate.of(2026, 10, 30))
    }

    "make the previous week the Monday to Friday of the week before the current one" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousWeek, wednesday) shouldBe
        (LocalDate.of(2026, 10, 19) -> LocalDate.of(2026, 10, 23))
    }

    "make the previous 2 weeks start on the Monday two weeks back" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousTwoWeeks, wednesday) shouldBe
        (LocalDate.of(2026, 10, 12) -> LocalDate.of(2026, 10, 23))
    }

    "make the previous 3 weeks start on the Monday three weeks back" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousThreeWeeks, wednesday) shouldBe
        (LocalDate.of(2026, 10, 5) -> LocalDate.of(2026, 10, 23))
    }

    "make the previous 4 weeks start on the Monday four weeks back" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousFourWeeks, wednesday) shouldBe
        (LocalDate.of(2026, 9, 28) -> LocalDate.of(2026, 10, 23))
    }

    "lay the presets out as weeks in the first row and months in the second" in { _ =>
      MilestoneSummaryPreset.rows.map(_.map(_.label)) shouldBe Seq(
        Seq("Current week", "Previous week", "Previous 2 weeks", "Previous 3 weeks", "Previous 4 weeks"),
        Seq("Current month", "Last month", "Last 2 months", "Last 3 months", "Last 6 months")
      )
    }

    "make last month the whole month before the current one" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.LastMonth, wednesday) shouldBe
        (LocalDate.of(2026, 9, 1) -> LocalDate.of(2026, 9, 30))
    }

    "make last 2 months end on the last day of the month before the current one" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.LastTwoMonths, wednesday) shouldBe
        (LocalDate.of(2026, 8, 1) -> LocalDate.of(2026, 9, 30))
    }

    "make last 3 months start three months back" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.LastThreeMonths, wednesday) shouldBe
        (LocalDate.of(2026, 7, 1) -> LocalDate.of(2026, 9, 30))
    }

    "make last 6 months start six months back" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.LastSixMonths, wednesday) shouldBe
        (LocalDate.of(2026, 4, 1) -> LocalDate.of(2026, 9, 30))
    }

    "make the current month the first to the last day of the current month" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.CurrentMonth, wednesday) shouldBe
        (LocalDate.of(2026, 10, 1) -> LocalDate.of(2026, 10, 31))
    }

    "take the previous week of a Sunday from the week that Sunday belongs to" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousWeek, LocalDate.of(2026, 11, 1)) shouldBe
        (LocalDate.of(2026, 10, 19) -> LocalDate.of(2026, 10, 23))
    }

    "take the previous week of a Monday from the week before it" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.PreviousWeek, LocalDate.of(2026, 10, 26)) shouldBe
        (LocalDate.of(2026, 10, 19) -> LocalDate.of(2026, 10, 23))
    }

    "cross the year boundary for last 3 months in January" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.LastThreeMonths, LocalDate.of(2027, 1, 15)) shouldBe
        (LocalDate.of(2026, 10, 1) -> LocalDate.of(2026, 12, 31))
    }

    "handle the length of February in the current month" in { _ =>
      MilestoneSummaryPresets.range(MilestoneSummaryPreset.CurrentMonth, LocalDate.of(2028, 2, 10)) shouldBe
        (LocalDate.of(2028, 2, 1) -> LocalDate.of(2028, 2, 29))
    }
  }
}
