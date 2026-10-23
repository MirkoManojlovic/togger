package com.togger.ui.task

import com.togger.common.TgIds
import com.togger.dto.TaskFilter
import com.togger.routes.TaskListParams
import com.togger.task.TaskPriority

import java.util.UUID
import scala.util.Try

/** What a filter widget has: every option it offers and the ones currently ticked. */
case class FilterSelection(allIds: Set[String], selectedIds: Set[String]) {

  /** The ids to send to the backend. Nothing ticked and everything ticked both mean "do not filter" - the widgets
    * always did - so neither is sent.
    */
  def idsToFilterBy: Set[String] =
    if (selectedIds.isEmpty || allIds.subsetOf(selectedIds)) Set.empty else selectedIds
}

/** Maps the widget selections of the task list to the backend's [[TaskFilter]] and back to URL params. */
object TaskListFilterMapping {

  /** Id of the "No milestone" option of the milestone widget. */
  val NoMilestoneId: String = "none"

  /** Id of the "Not assigned" option of the assignee widget. */
  val UnassignedId: String = TgIds.UNASSIGNED_UUID.toString

  def toTaskFilter(
    statuses: FilterSelection,
    priorities: FilterSelection,
    labels: FilterSelection,
    assignees: FilterSelection,
    milestones: FilterSelection,
    search: String,
    includeArchived: Boolean = false
  ): TaskFilter =
    TaskFilter(
      statusIds = uuids(statuses.idsToFilterBy),
      priorities = priorities.idsToFilterBy.toList.flatMap(_.toIntOption).flatMap(TaskPriority.withValueOpt),
      labelIds = uuids(labels.idsToFilterBy),
      assigneeIds = uuids(assignees.idsToFilterBy),
      milestoneIds = uuids(milestones.idsToFilterBy.map(noMilestoneAsUuid)),
      search = Option(search.trim).filter(_.nonEmpty),
      includeArchived = includeArchived
    )

  /** What goes into the URL for a widget: nothing while it still has the selection the screen starts with. */
  def toParamSelection(selectedIds: Set[String], defaultSelectedIds: Set[String]): Option[Set[String]] =
    Option.when(selectedIds != defaultSelectedIds)(selectedIds)

  /** The ticked options the URL implies. */
  def selectedFromParams(chosen: Option[Set[String]], defaultSelectedIds: Set[String]): Set[String] =
    chosen.getOrElse(defaultSelectedIds)

  def withSearch(params: TaskListParams, search: String): TaskListParams =
    params.firstPage.copy(search = search.trim)

  private def noMilestoneAsUuid(id: String): String =
    if (id == NoMilestoneId) TgIds.NO_MILESTONE_UUID.toString else id

  private def uuids(ids: Set[String]): List[UUID] =
    ids.toList.sorted.flatMap(id => Try(UUID.fromString(id)).toOption)
}
