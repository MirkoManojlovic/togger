package com.togger.routes

import com.togger.dto.ContributionsFilter

import java.time.LocalDate
import scala.util.Try

/** The day range of the contributions page, as the URL spells it; no range is every day. */
case class ContributionsParams(from: Option[LocalDate] = None, to: Option[LocalDate] = None) {
  def filter: ContributionsFilter = ContributionsFilter(from, to)
}

object ContributionsParams {
  val default: ContributionsParams = ContributionsParams()

  /** A hand-edited URL never gets to the backend as a 400: a day that is not a date, or a range that ends before it
    * starts, is left out.
    */
  def fromQuery(from: Option[String], to: Option[String]): ContributionsParams = {
    val start = from.flatMap(parseDate)
    val end = to.flatMap(parseDate)
    if (start.zip(end).exists { case (s, e) => e.isBefore(s) }) default else ContributionsParams(start, end)
  }

  def dateToQuery(date: Option[LocalDate]): Option[String] = date.map(_.toString)

  private def parseDate(value: String): Option[LocalDate] = Try(LocalDate.parse(value.trim)).toOption
}
