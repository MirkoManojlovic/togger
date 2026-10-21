package com.togger.ui.contributions

import com.togger.routes.ContributionsParams
import com.togger.ui.milestone.{ActiveDateFilter, MonthOption}

import java.time.{LocalDate, YearMonth}

/** How the date filter of the page becomes the range asked from the backend. */
object ContributionsFilterMapping {

  val MonthsOffered = 12

  /** The months a page opened without a range looks back, up to the day it is opened. */
  val DefaultMonths = 3

  /** A URL without a range is the last [[DefaultMonths]] months. The range is then in the address bar, so a reload or
    * a shared link shows the same days. Every day is still one pick away, by ticking no month.
    */
  def withDefaultRange(params: ContributionsParams, today: LocalDate): ContributionsParams =
    if (params == ContributionsParams.default)
      ContributionsParams(Some(today.minusMonths(DefaultMonths.toLong)), Some(today))
    else params

  /** The months up to the one of `today`, the latest first. */
  def monthOptions(today: LocalDate): Seq[MonthOption] =
    (0 until MonthsOffered).map { back =>
      val month = YearMonth.from(today).minusMonths(back.toLong)
      MonthOption(month.getYear, month.getMonthValue)
    }

  /** Months that are not next to each other are spanned: the backend takes one range. No month ticked is every day. */
  def paramsOf(filter: ActiveDateFilter): ContributionsParams = filter match {
    case ActiveDateFilter.ByRange(start, end) =>
      ContributionsParams.fromQuery(start.map(_.toString), end.map(_.toString))
    case ActiveDateFilter.ByMonth(selected) =>
      val months = selected.map { case (year, month) => YearMonth.of(year, month) }
      if (months.isEmpty) ContributionsParams.default
      else ContributionsParams(Some(months.min.atDay(1)), Some(months.max.atEndOfMonth))
  }
}
