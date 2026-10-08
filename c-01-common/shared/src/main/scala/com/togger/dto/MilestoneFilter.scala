package com.togger.dto

import com.togger.milestone.MilestoneStatus

/** Filters of the milestone list, read on the latest version of each milestone. An empty list (or no search) leaves
  * that dimension unfiltered. Dimensions are combined with AND, statuses with OR. `search` is a case-insensitive
  * substring of name or description.
  */
case class MilestoneFilter(
  statuses: List[MilestoneStatus] = List.empty,
  search: Option[String] = None
)

object MilestoneFilter {
  val empty: MilestoneFilter = MilestoneFilter()
}
