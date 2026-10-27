package com.togger.ui.milestone

import com.togger.dto.SummariseMilestoneWithAiReq

import java.time.LocalDate
import java.util.UUID

object MilestoneSummaryAi {

  val Model = "gemma4:e4b"

  val DefaultPrompt: String =
    "Summarise the progress made on this milestone from the following log entries as a short status report " +
      "for stakeholders. Group the work by theme, call out anything blocked, and keep it concise. " +
      "The log entries are a JSON array:"

  def request(milestoneId: UUID, startDate: LocalDate, endDate: LocalDate): SummariseMilestoneWithAiReq =
    SummariseMilestoneWithAiReq(
      milestoneId = milestoneId,
      promptId = None,
      summarizePrompt = DefaultPrompt,
      startDate = startDate,
      endDate = endDate,
      model = Model
    )
}
