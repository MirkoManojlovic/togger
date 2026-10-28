package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec

import java.time.{DayOfWeek, LocalDate}

class MilestoneSummaryRangeCalendarTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "start the only calendar of a week on its Monday" in { _ =>
      // 2026-10-28 is a Wednesday
      MilestoneSummaryRangeCalendar.calendarStarts(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30)) shouldBe
        Seq(LocalDate.of(2026, 10, 26))
    }

    "keep a range of four weeks in one calendar" in { _ =>
      MilestoneSummaryRangeCalendar.calendarStarts(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 1)) shouldBe
        Seq(LocalDate.of(2026, 10, 5))
    }

    "add a calendar, the oldest first, when the range goes past four weeks" in { _ =>
      MilestoneSummaryRangeCalendar.calendarStarts(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 2)) shouldBe
        Seq(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 11, 2))
    }

    "cover six months with calendars in date order" in { _ =>
      val starts = MilestoneSummaryRangeCalendar.calendarStarts(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 9, 30))
      (starts.head, starts.size, starts == starts.sorted) shouldBe ((LocalDate.of(2026, 3, 30), 7, true))
    }

    "show nothing for a range that ends before it starts" in { _ =>
      MilestoneSummaryRangeCalendar.calendarStarts(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 1)) shouldBe
        Seq.empty
    }

    "start a range of one day with the first click" in { _ =>
      val day = LocalDate.of(2026, 10, 10)
      MilestoneSummaryRangeCalendar.pick(None, day) shouldBe (((day, day), Some(day)))
    }

    "end the range with the second click" in { _ =>
      val first = LocalDate.of(2026, 10, 10)
      val second = LocalDate.of(2026, 10, 20)
      MilestoneSummaryRangeCalendar.pick(Some(first), second) shouldBe (((first, second), None))
    }

    "put the earlier of the two days first when the second click is before the first" in { _ =>
      val first = LocalDate.of(2026, 10, 20)
      val second = LocalDate.of(2026, 10, 10)
      MilestoneSummaryRangeCalendar.pick(Some(first), second) shouldBe (((second, first), None))
    }

    "keep a one day range when the second click is on the first day" in { _ =>
      val day = LocalDate.of(2026, 10, 10)
      MilestoneSummaryRangeCalendar.pick(Some(day), day) shouldBe (((day, day), None))
    }

    "show only the work days, in the order of the week" in { _ =>
      MilestoneSummaryRangeCalendar.shownDays(Set(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)) shouldBe
        Seq(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    }

    "show every day while no work days are known" in { _ =>
      MilestoneSummaryRangeCalendar.shownDays(Set.empty) shouldBe DayOfWeek.values.toSeq
    }
  }
}
