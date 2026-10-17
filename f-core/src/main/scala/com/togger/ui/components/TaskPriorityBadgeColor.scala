package com.togger.ui.components

import com.togger.task.TaskPriority

/** The [[TgBadgeColor]] of a task priority, the same hue as it has in charts. */
object TaskPriorityBadgeColor {

  def of(priority: TaskPriority): TgBadgeColor = ChartColorBadgeColor.of(priority)

  implicit class TaskPriorityBadgeColorOps(private val priority: TaskPriority) extends AnyVal {
    def badgeColor: TgBadgeColor = of(priority)
  }
}
