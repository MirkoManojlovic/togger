package com.togger.milestone

import com.togger.common.TgError

object MilestoneAiSummaryError {
  sealed trait MilestoneAiSummaryError extends TgError

  case object NoLogEntriesInRange extends MilestoneAiSummaryError {
    override def message: String = "The milestone has no log entries between the given dates"
  }

  case class TooManyLogEntries(limit: Int) extends MilestoneAiSummaryError {
    override def message: String = s"More than $limit log entries in the given dates, narrow the date range"
  }
}
