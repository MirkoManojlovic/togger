package com.togger.ui.components

import com.togger.chart.TaskLabelChartable
import com.togger.dto.FindTaskLabelResp

/** The [[TgBadgeColor]] of a task label, the same hue as it has in charts. A label name that is not one of the
  * default labels is [[TgBadgeColor.Gray]], see [[TaskLabelChartable]].
  */
object TaskLabelBadgeColor {

  def of(labelName: String): TgBadgeColor = ChartColorBadgeColor.of(TaskLabelChartable.of(labelName))

  /** Lets callers write `taskLabel.badgeColor` instead of `TaskLabelBadgeColor.of(taskLabel.name)`. */
  implicit class TaskLabelBadgeColorOps(private val taskLabel: FindTaskLabelResp) extends AnyVal {
    def badgeColor: TgBadgeColor = of(taskLabel.name)

    /** The label name with its emoji in front, as badges show it. */
    def badgeText: String = s"${taskLabel.emoji} ${taskLabel.name}".trim
  }

}
