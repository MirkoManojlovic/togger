package com.togger.ui.milestone

import com.togger.common.suites.AsyncJsWordSpec

import java.time.LocalDate
import java.util.UUID

class MilestoneSummaryAiTest extends AsyncJsWordSpec {

  testNameFromClassName should {

    "ask gemma4:e4b with the default prompt and no stored prompt for the period of the milestone" in { _ =>
      val milestoneId = UUID.randomUUID()
      val start = LocalDate.of(2026, 10, 26)
      val end = LocalDate.of(2026, 10, 30)
      val request = MilestoneSummaryAi.request(milestoneId, start, end)
      (request.model, request.promptId, request.summarizePrompt, request.milestoneId, request.startDate, request.endDate) shouldBe
        ("gemma4:e4b", None, MilestoneSummaryAi.DefaultPrompt, milestoneId, start, end)
    }
  }
}
