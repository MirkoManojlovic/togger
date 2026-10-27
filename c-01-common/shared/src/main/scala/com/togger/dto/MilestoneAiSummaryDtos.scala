package com.togger.dto

import java.time.LocalDate
import java.util.UUID

case class SummariseMilestoneWithAiReq(
  milestoneId: UUID,
  promptId: Option[UUID],
  summarizePrompt: String,
  startDate: LocalDate,
  endDate: LocalDate,
  model: String
)

case class SummariseMilestoneWithAiResp(summary: String)
