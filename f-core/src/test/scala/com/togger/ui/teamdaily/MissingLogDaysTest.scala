package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class MissingLogDaysTest extends AsyncJsWordSpec {

  private val today = LocalDate.of(2026, 10, 21)

  testNameFromClassName should {

    "ask about today when the task has no entry" in { _ =>
      MissingLogDays.of(None, today) shouldBe Seq(today)
    }

    "ask about today when the last entry is older" in { _ =>
      MissingLogDays.of(Some(today.minusDays(5)), today) shouldBe Seq(today)
    }

    "have nothing missing when the last entry is today" in { _ =>
      MissingLogDays.of(Some(today), today) shouldBe Seq.empty
    }
  }
}
