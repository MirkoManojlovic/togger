package com.togger.ui.teamdaily

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate

class MissingLogDaysTest extends AsyncJsWordSpec {

  private val today = LocalDate.of(2026, 10, 21)

  testNameFromClassName should {

    "ask about today" in { _ =>
      MissingLogDays.of(today) shouldBe Seq(today)
    }
  }
}
