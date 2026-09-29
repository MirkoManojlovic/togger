package com.togger.ui.components

import com.togger.task.TaskPriority

object TaskPriorityBadgeColor {
  private val colorByPriority: Map[TaskPriority, TgBadgeColor] = Map(
    TaskPriority.Critical -> TgBadgeColor.Red,
    TaskPriority.High -> TgBadgeColor.Orange,
    TaskPriority.Medium -> TgBadgeColor.Yellow,
    TaskPriority.Low -> TgBadgeColor.Lime,
    TaskPriority.Planned -> TgBadgeColor.Green
  )

  def of(priority: TaskPriority): TgBadgeColor = colorByPriority.getOrElse(priority, TgBadgeColor.Gray)

  implicit class TaskPriorityBadgeColorOps(private val priority: TaskPriority) extends AnyVal {
    def badgeColor: TgBadgeColor = of(priority)
  }
}
