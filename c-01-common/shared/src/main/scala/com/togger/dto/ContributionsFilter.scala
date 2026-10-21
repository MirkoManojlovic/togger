package com.togger.dto

import java.time.LocalDate

/** Optional date range of a contributions query, both ends inclusive and in UTC days. */
case class ContributionsFilter(from: Option[LocalDate] = None, to: Option[LocalDate] = None)

object ContributionsFilter {
  val empty: ContributionsFilter = ContributionsFilter()
}
