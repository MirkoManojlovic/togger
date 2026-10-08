package com.togger.routes

import com.togger.common.PageRequest
import com.togger.milestone.MilestoneStatus

/** What the milestone list shows, as carried by the URL. `statuses` of `None` is the screen's default selection (the
  * milestones in progress), `Some(ids)` is what the user picked (possibly nothing, which the URL spells `_empty`).
  */
case class MilestoneListParams(
  page: Int = PageRequest.DefaultPage,
  size: Int = PageRequest.DefaultSize,
  statuses: Option[Set[String]] = None,
  search: String = ""
) {
  def pageRequest: PageRequest = PageRequest(page, size)

  /** Another page of the same list. */
  def atPage(newPage: Int, newSize: Int): MilestoneListParams = copy(page = newPage, size = newSize)

  /** The same list after a filter change: back to the first page. */
  def firstPage: MilestoneListParams = copy(page = PageRequest.DefaultPage)

  /** The status values ticked: what the URL picked, or the default. */
  def selectedStatuses: Set[String] = statuses.getOrElse(MilestoneListParams.DefaultStatuses)
}

object MilestoneListParams {
  val default: MilestoneListParams = MilestoneListParams()

  val DefaultStatuses: Set[String] = Set(MilestoneStatus.InProgress.value.toString)

  /** A hand-edited URL never gets to the backend as a 400: bad page or size fall back to the defaults. */
  def fromQuery(
    page: Option[String],
    size: Option[String],
    statuses: List[String],
    search: Option[String]
  ): MilestoneListParams =
    MilestoneListParams(
      page = page.flatMap(_.trim.toIntOption).filter(_ >= 1).getOrElse(PageRequest.DefaultPage),
      size = size.flatMap(_.trim.toIntOption).filter(PageRequest.SizeOptions.contains).getOrElse(PageRequest.DefaultSize),
      statuses = TaskListParams.selectionFromQuery(statuses),
      search = search.map(_.trim).getOrElse("")
    )

  def pageToQuery(params: MilestoneListParams): Option[String] =
    Option.when(params.page != PageRequest.DefaultPage)(params.page.toString)

  def sizeToQuery(params: MilestoneListParams): Option[String] =
    Option.when(params.size != PageRequest.DefaultSize)(params.size.toString)

  def searchToQuery(params: MilestoneListParams): Option[String] =
    Option.when(params.search.nonEmpty)(params.search)
}
