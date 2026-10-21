package com.togger.ui.contributions

import com.togger.dto.MemberContributionDto
import com.togger.ui.timebox.StagedGrouping

case class StatCount(label: String, count: Int)

case class StatCountGroup(title: String, counts: Seq[StatCount])

/** The counts behind a member's charts, to show next to their numbers: how many log entries are in progress, how many
  * tasks are blocked, critical, labelled a bug, and so on.
  */
object MemberStatCounts {

  /** The groups that have something in them, log entries first. A task with two labels is in both. */
  def groupsOf(member: MemberContributionDto): Seq[StatCountGroup] =
    Seq(
      StatCountGroup(
        "Log entries by status",
        member.entriesByStatus.map(entry => StatCount(entry.status.label, entry.count))
      ),
      StatCountGroup("Tasks by status", member.tasks.byStatus.map(status => StatCount(status.name, status.count))),
      StatCountGroup(
        "Tasks by priority",
        member.tasks.byPriority.map(task => StatCount(task.priority.priorityName, task.count))
      ),
      StatCountGroup(
        "Tasks by label",
        member.tasks.byLabel.map(label => StatCount(label.name.getOrElse(StagedGrouping.NoLabel), label.count))
      )
    ).filter(_.counts.nonEmpty)
}
