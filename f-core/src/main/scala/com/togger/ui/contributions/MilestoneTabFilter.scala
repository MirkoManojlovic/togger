package com.togger.ui.contributions

import com.togger.dto.MilestoneContributionsDto
import com.togger.milestone.MilestoneStatus
import com.togger.ui.components.MultiSelectItem

/** Which milestones get a tab. A project collects milestones, so only those in progress have one until the filter asks
  * for the others.
  */
object MilestoneTabFilter {

  val DefaultStatuses: Set[MilestoneStatus] = Set(MilestoneStatus.InProgress)

  def idOf(status: MilestoneStatus): String = status.value.toString

  val DefaultIds: Set[String] = DefaultStatuses.map(idOf)

  /** One item for every status, those of [[DefaultStatuses]] ticked. */
  val items: Seq[MultiSelectItem] =
    MilestoneStatus.values.sortBy(_.value).map { status =>
      MultiSelectItem(idOf(status), status.statusName, isSelected = DefaultStatuses.contains(status))
    }

  def statusesOf(ids: Set[String]): Set[MilestoneStatus] =
    MilestoneStatus.values.filter(status => ids.contains(idOf(status))).toSet

  /** The milestones with a status the filter lets through. The tasks without a milestone have no status, so they always
    * stay.
    */
  def visible(milestones: Seq[MilestoneContributionsDto], ids: Set[String]): Seq[MilestoneContributionsDto] = {
    val statuses = statusesOf(ids)
    milestones.filter(_.status.forall(statuses.contains))
  }
}
