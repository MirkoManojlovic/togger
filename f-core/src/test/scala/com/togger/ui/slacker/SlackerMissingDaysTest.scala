package com.togger.ui.slacker

import com.togger.common.WorkDayCalendar
import com.togger.common.suites.AsyncJsWordSpec

import java.time.{DayOfWeek, LocalDate}

class SlackerMissingDaysTest extends AsyncJsWordSpec {

  private val monday = LocalDate.of(2026, 10, 19)
  private val workDays = WorkDayCalendar.DefaultWorkDays

  testNameFromClassName should {

    "list every work day from the start through today, skipping the weekend" in { _ =>
      SlackerMissingDays.of(Seq.empty, monday.minusDays(3), monday.plusDays(1), workDays) shouldBe
        Seq(monday.minusDays(3), monday, monday.plusDays(1))
    }

    "skip the days that have an entry, gaps between entries included" in { _ =>
      SlackerMissingDays.of(Seq(monday.plusDays(1)), monday, monday.plusDays(2), workDays) shouldBe
        Seq(monday, monday.plusDays(2))
    }

    "have nothing missing when the task starts after today" in { _ =>
      SlackerMissingDays.of(Seq.empty, monday.plusDays(1), monday, workDays) shouldBe Seq.empty
    }

    "ask only about today when the task starts today" in { _ =>
      SlackerMissingDays.of(Seq.empty, monday, monday, workDays) shouldBe Seq(monday)
    }

    "have nothing missing when today is logged and the rest too" in { _ =>
      SlackerMissingDays.of(Seq(monday), monday, monday, workDays) shouldBe Seq.empty
    }

    "follow a custom work week" in { _ =>
      SlackerMissingDays.of(Seq.empty, monday, monday.plusDays(6), Set(DayOfWeek.SATURDAY)) shouldBe
        Seq(monday.plusDays(5))
    }

    "have nothing missing without work days" in { _ =>
      SlackerMissingDays.of(Seq.empty, monday, monday.plusDays(6), Set.empty) shouldBe Seq.empty
    }
  }
}
