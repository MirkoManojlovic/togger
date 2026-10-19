package com.togger.routes

import com.togger.milestone.MilestoneStatus

import java.time.LocalDate
import scala.util.Try

/** What the milestone history shows, as carried by the URL. A selection of `None` is the filter's default (everything
  * ticked, or for the milestone status the milestones in progress), `Some(ids)` is what the user picked (possibly
  * nothing, which the URL spells `_empty`).
  */
case class MilestoneHistoryParams(
  byRange: Boolean = false,
  months: Option[Set[String]] = None,
  start: Option[LocalDate] = None,
  stop: Option[LocalDate] = None,
  taskStatuses: Option[Set[String]] = None,
  milestones: Option[Set[String]] = None,
  milestoneStatuses: Option[Set[String]] = None,
  search: String = ""
) {

  /** The milestone status values ticked: what the URL picked, or the default. */
  def selectedMilestoneStatuses: Set[String] = milestoneStatuses.getOrElse(MilestoneHistoryParams.DefaultMilestoneStatuses)
}

object MilestoneHistoryParams {
  val default: MilestoneHistoryParams = MilestoneHistoryParams()

  val DefaultMilestoneStatuses: Set[String] = Set(MilestoneStatus.InProgress.value.toString)

  private val RangeMode = "range"

  /** A hand-edited URL never breaks the page: what cannot be read falls back to the default. */
  def fromQuery(
    mode: Option[String],
    months: List[String],
    start: Option[String],
    stop: Option[String],
    taskStatuses: List[String],
    milestones: List[String],
    milestoneStatuses: List[String],
    search: Option[String]
  ): MilestoneHistoryParams =
    MilestoneHistoryParams(
      byRange = mode.exists(_.trim == RangeMode),
      months = TaskListParams.selectionFromQuery(months),
      start = start.flatMap(parseDate),
      stop = stop.flatMap(parseDate),
      taskStatuses = TaskListParams.selectionFromQuery(taskStatuses),
      milestones = TaskListParams.selectionFromQuery(milestones),
      milestoneStatuses = TaskListParams.selectionFromQuery(milestoneStatuses),
      search = search.map(_.trim).getOrElse("")
    )

  private def parseDate(value: String): Option[LocalDate] =
    Try(LocalDate.parse(value.trim)).toOption

  def modeToQuery(params: MilestoneHistoryParams): Option[String] =
    Option.when(params.byRange)(RangeMode)

  def dateToQuery(date: Option[LocalDate]): Option[String] =
    date.map(_.toString)

  def searchToQuery(params: MilestoneHistoryParams): Option[String] =
    Option.when(params.search.nonEmpty)(params.search)
}
