package com.togger.ui.components

import com.togger.chart.TaskStatusChartable
import com.togger.dto.FindTaskStatusResp

/** The [[TgBadgeColor]] of a task's status, the same hue as it has in charts. A status name that is not one of the
  * default statuses is [[TgBadgeColor.Gray]], see [[TaskStatusChartable]].
  */
object TaskStatusBadgeColor {

  def of(statusName: String): TgBadgeColor = ChartColorBadgeColor.of(TaskStatusChartable.of(statusName))

  /** Lets callers write `taskStatus.badgeColor` instead of `TaskStatusBadgeColor.of(taskStatus.name)`. */
  implicit class TaskStatusBadgeColorOps(private val taskStatus: FindTaskStatusResp) extends AnyVal {
    def badgeColor: TgBadgeColor = of(taskStatus.name)

    /** The status name with its emoji in front, as badges show it. */
    def badgeText: String = s"${taskStatus.emoji} ${taskStatus.name}".trim
  }

}
