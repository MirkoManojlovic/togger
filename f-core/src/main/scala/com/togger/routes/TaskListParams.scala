package com.togger.routes

import com.togger.common.PageRequest

/** What the task list shows, as carried by the URL. A filter of `None` is the screen's default selection, `Some(ids)` is
  * what the user picked (possibly nothing, which the URL spells `_empty`).
  */
case class TaskListParams(
  page: Int = PageRequest.DefaultPage,
  size: Int = PageRequest.DefaultSize,
  statuses: Option[Set[String]] = None,
  priorities: Option[Set[String]] = None,
  labels: Option[Set[String]] = None,
  assignees: Option[Set[String]] = None,
  milestones: Option[Set[String]] = None,
  search: String = ""
) {
  def pageRequest: PageRequest = PageRequest(page, size)

  /** Another page of the same list. */
  def atPage(newPage: Int, newSize: Int): TaskListParams = copy(page = newPage, size = newSize)

  /** The same list after a filter change: back to the first page. */
  def firstPage: TaskListParams = copy(page = PageRequest.DefaultPage)
}

object TaskListParams {
  val default: TaskListParams = TaskListParams()

  private val EmptySelectionMarker = "_empty"

  /** A hand-edited URL never gets to the backend as a 400: bad page or size fall back to the defaults. */
  def fromQuery(
    page: Option[String],
    size: Option[String],
    statuses: List[String],
    priorities: List[String],
    labels: List[String],
    assignees: List[String],
    milestones: List[String],
    search: Option[String]
  ): TaskListParams =
    TaskListParams(
      page = page.flatMap(_.trim.toIntOption).filter(_ >= 1).getOrElse(PageRequest.DefaultPage),
      size = size.flatMap(_.trim.toIntOption).filter(PageRequest.SizeOptions.contains).getOrElse(PageRequest.DefaultSize),
      statuses = selectionFromQuery(statuses),
      priorities = selectionFromQuery(priorities),
      labels = selectionFromQuery(labels),
      assignees = selectionFromQuery(assignees),
      milestones = selectionFromQuery(milestones),
      search = search.map(_.trim).getOrElse("")
    )

  def pageToQuery(params: TaskListParams): Option[String] =
    Option.when(params.page != PageRequest.DefaultPage)(params.page.toString)

  def sizeToQuery(params: TaskListParams): Option[String] =
    Option.when(params.size != PageRequest.DefaultSize)(params.size.toString)

  def searchToQuery(params: TaskListParams): Option[String] =
    Option.when(params.search.nonEmpty)(params.search)

  def selectionFromQuery(values: List[String]): Option[Set[String]] =
    if (values.isEmpty) None
    else if (values == List(EmptySelectionMarker)) Some(Set.empty)
    else Some(values.toSet)

  def selectionToQuery(selection: Option[Set[String]]): List[String] =
    selection match {
      case None                       => List.empty
      case Some(ids) if ids.isEmpty => List(EmptySelectionMarker)
      case Some(ids)                  => ids.toList.sorted
    }
}
