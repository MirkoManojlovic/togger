package com.togger.ui.timebox

import com.togger.chart.{ChartColor, TaskLabelChartable, TaskStatusChartable}
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull}
import com.togger.task.TaskPriority

/** A group of staged tasks under one heading; `title` is empty when the tasks are not grouped. `chartColor` is the color
  * the group has in charts, when what it groups by has one.
  */
case class StagedGroup(title: String, tasks: Seq[FindTaskRespFull], chartColor: Option[ChartColor] = None)

/** The ways the staged tasks can be laid out, one per tab. */
sealed abstract class StagedGrouping(val id: String, val label: String) {

  def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup]
}

object StagedGrouping {

  val NoAssignee = "Unassigned"
  val NoMilestone = "No milestone"
  val NoLabel = "No label"

  /** Groups in the order of `keys`, with the tasks that have no key last. A task lands in every group it has a key for. */
  private def grouped(
    tasks: Seq[FindTaskRespFull],
    emptyTitle: String,
    colorOf: String => Option[ChartColor] = _ => None
  )(keys: FindTaskRespFull => Seq[(Int, String)]): Seq[StagedGroup] = {
    val keyed = tasks.flatMap(task => keys(task).map(_ -> task))
    val withKey = keyed
      .groupMap { case (key, _) => key }(_._2)
      .toSeq
      .sortBy { case ((order, title), _) => (order, title) }
      .map { case ((_, title), group) => StagedGroup(title, group, colorOf(title)) }
    val withoutKey = tasks.filter(keys(_).isEmpty)
    if (withoutKey.isEmpty) withKey else withKey :+ StagedGroup(emptyTitle, withoutKey)
  }

  case object NotGrouped extends StagedGrouping("none", "Total") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] =
      Seq(StagedGroup("", tasks))
  }

  case object ByAssignee extends StagedGrouping("assignee", "Assignee") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] =
      grouped(tasks, NoAssignee)(task => task.assignedTo.map(0 -> _.fullName).toSeq)
  }

  case object ByMilestone extends StagedGrouping("milestone", "Milestone") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] = {
      val statusByName = milestones.map(milestone => milestone.name -> milestone.status.chartColor).toMap
      grouped(tasks, NoMilestone, statusByName.get)(task =>
        task.milestone.flatMap(id => milestones.find(_.id == id)).map(0 -> _.name).toSeq
      )
    }
  }

  private def priorityColor(shortCode: String): Option[ChartColor] =
    TaskPriority.values.find(_.shortCode == shortCode).map(_.chartColor)

  case object ByPriority extends StagedGrouping("priority", "Priority") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] =
      grouped(tasks, "", priorityColor)(task => Seq(task.priority.value -> task.priority.shortCode))
  }

  case object ByLabel extends StagedGrouping("label", "Label") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] =
      grouped(tasks, NoLabel, title => Some(TaskLabelChartable.of(title).chartColor))(_.taskLabels.map(0 -> _.name))
  }

  case object ByStatus extends StagedGrouping("status", "Status") {
    def group(tasks: Seq[FindTaskRespFull], milestones: Seq[FindMilestoneResp]): Seq[StagedGroup] =
      grouped(tasks, "", title => Some(TaskStatusChartable.of(title).chartColor))(task => Seq(0 -> task.taskStatus.name))
  }

  val all: Seq[StagedGrouping] = Seq(NotGrouped, ByAssignee, ByMilestone, ByPriority, ByLabel, ByStatus)
}
