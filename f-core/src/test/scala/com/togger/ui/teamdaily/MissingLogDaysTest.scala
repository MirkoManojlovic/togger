package com.togger.ui.teamdaily

import com.togger.common.WorkDayCalendar
import com.togger.common.suites.AsyncJsWordSpec

import java.time.{DayOfWeek, LocalDate}

class MissingLogDaysTest extends AsyncJsWordSpec {

  private val workDays = WorkDayCalendar.DefaultWorkDays
  private val friday = LocalDate.of(2026, 10, 16)
  private val monday = LocalDate.of(2026, 10, 19)
  private val wednesday = LocalDate.of(2026, 10, 21)

  testNameFromClassName should {

    "skip the weekend between the last entry and today" in { _ =>
      MissingLogDays.of(Some(friday), friday, wednesday, workDays) shouldBe
        Seq(monday, monday.plusDays(1), wednesday)
    }

    "have nothing missing when the last entry is today" in { _ =>
      MissingLogDays.of(Some(wednesday), friday, wednesday, workDays) shouldBe Seq.empty
    }

    "start at the task start when there is no entry" in { _ =>
      MissingLogDays.of(None, monday, wednesday, workDays) shouldBe
        Seq(monday, monday.plusDays(1), wednesday)
    }

    "only ask about today when the task starts in the future" in { _ =>
      MissingLogDays.of(None, wednesday.plusDays(5), wednesday, workDays) shouldBe Seq(wednesday)
    }

    "follow a custom work week" in { _ =>
      MissingLogDays.of(Some(friday), friday, monday, Set(DayOfWeek.SATURDAY, DayOfWeek.MONDAY)) shouldBe
        Seq(friday.plusDays(1), monday)
    }

    "only keep the latest days when there are too many" in { _ =>
      val days = MissingLogDays.of(None, wednesday.minusYears(1), wednesday, workDays)
      days should have size MissingLogDays.MaxDays.toLong
      days.last shouldBe wednesday
    }

    "reverse for today first" in { _ =>
      MissingLogDays.inFillOrder(Seq(monday, wednesday), todayFirst = true) shouldBe Seq(wednesday, monday)
    }
  }
}
