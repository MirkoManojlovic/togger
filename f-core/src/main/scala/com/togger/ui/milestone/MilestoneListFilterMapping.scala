package com.togger.ui.milestone

import com.togger.dto.MilestoneFilter
import com.togger.milestone.MilestoneStatus
import com.togger.routes.MilestoneListParams

/** Maps what the milestone list shows, as carried by the URL, to the backend's [[MilestoneFilter]]. */
object MilestoneListFilterMapping {

  private val allStatusIds: Set[String] = MilestoneStatus.values.map(_.value.toString).toSet

  /** Nothing ticked and everything ticked both mean "do not filter" - the widget always did - so neither is sent. */
  def toMilestoneFilter(params: MilestoneListParams): MilestoneFilter = {
    val selected = params.selectedStatuses
    val statusesToFilterBy =
      if (selected.isEmpty || allStatusIds.subsetOf(selected)) List.empty
      else selected.toList.sorted.flatMap(_.toIntOption).flatMap(MilestoneStatus.withValueOpt)
    MilestoneFilter(statusesToFilterBy, Option(params.search.trim).filter(_.nonEmpty))
  }

  /** What goes into the URL for the status widget: nothing while it still has the selection the screen starts with. */
  def toParamSelection(picked: Set[String]): Option[Set[String]] =
    Option.when(picked != MilestoneListParams.DefaultStatuses)(picked)
}
