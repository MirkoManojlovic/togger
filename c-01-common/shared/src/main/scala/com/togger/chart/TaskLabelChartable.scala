package com.togger.chart

import com.togger.dto.FindTaskLabelResp
import com.togger.task.DefaultTaskLabel

sealed abstract class TaskLabelChartable(val defaultLabel: DefaultTaskLabel, val chartColor: ChartColor)
    extends Chartable {
  val key: String = s"task-label.${defaultLabel.entryName}"
  val label: String = defaultLabel.labelName
}

object TaskLabelFeatureChartable extends TaskLabelChartable(DefaultTaskLabel.Feature, ChartColor.Green)
object TaskLabelBugChartable extends TaskLabelChartable(DefaultTaskLabel.Bug, ChartColor.Red)
object TaskLabelRefactorChartable extends TaskLabelChartable(DefaultTaskLabel.Refactor, ChartColor.Purple)
object TaskLabelTestChartable extends TaskLabelChartable(DefaultTaskLabel.Test, ChartColor.Blue)
object TaskLabelDocsChartable extends TaskLabelChartable(DefaultTaskLabel.Docs, ChartColor.Cyan)
object TaskLabelChoreChartable extends TaskLabelChartable(DefaultTaskLabel.Chore, ChartColor.Gray)

/** A project's task labels are free-form rows rather than [[DefaultTaskLabel]], so a label is matched to its
  * [[Chartable]] by name; a name that matches none is gray.
  */
object TaskLabelChartable {

  val all: Seq[TaskLabelChartable] = Seq(
    TaskLabelFeatureChartable,
    TaskLabelBugChartable,
    TaskLabelRefactorChartable,
    TaskLabelTestChartable,
    TaskLabelDocsChartable,
    TaskLabelChoreChartable
  )

  def of(labelName: String): Chartable =
    all.find(_.label == labelName).getOrElse(Chartable.Unknown(s"task-label.$labelName", labelName))

  def of(taskLabel: FindTaskLabelResp): Chartable = of(taskLabel.name)

}
