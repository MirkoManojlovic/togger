package com.togger.milestone

import com.togger.dto.MilestoneFilter
import sttp.tapir._

/** The milestone list filters as query params: a repeatable `status` (the status value) and a single `search`. */
object MilestoneFilterInputs {

  private type FilterParams = (List[Int], Option[String])

  val milestoneFilterQuery: EndpointInput[MilestoneFilter] =
    query[List[Int]]("status")
      .and(query[Option[String]]("search"))
      .mapDecode(toFilter)(toParams)

  private def toFilter(params: FilterParams): DecodeResult[MilestoneFilter] = {
    val (statusValues, search) = params
    val statuses = statusValues.map(MilestoneStatus.withValueOpt)
    if (statuses.exists(_.isEmpty))
      DecodeResult.Error(statusValues.mkString(","), new IllegalArgumentException("Unknown milestone status"))
    else
      DecodeResult.Value(MilestoneFilter(statuses.flatten, search.filter(_.trim.nonEmpty)))
  }

  private def toParams(filter: MilestoneFilter): FilterParams =
    (filter.statuses.map(_.value), filter.search)
}
