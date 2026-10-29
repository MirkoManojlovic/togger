package com.togger.ui.teamdaily

import com.raquo.laminar.api.L._
import com.togger.ui.components.{TaskStatusBadgeColor, TgBadge, TgBadgeColor, TgBadgeSize}

/** A status group of the task picker: a card whose header names the status in its color, with the tasks beneath. */
object TeamDailyStatusGroupCard {

  def apply(group: TeamDailyStatusGroup, taskRow: TeamDailyTaskPlan => HtmlElement): HtmlElement = {
    val color = TaskStatusBadgeColor.of(group.statusName)
    val neutral = color == TgBadgeColor.Gray || color == TgBadgeColor.Alternative
    div(
      cls("mb-3 ms-3 rounded-base border border-default-medium bg-neutral-primary-soft overflow-hidden"),
      div(
        cls("flex items-center gap-2 px-3 py-2 border-b border-default-medium bg-neutral-secondary-medium"),
        TgBadge(
          text = group.statusLabel,
          color = color,
          size = TgBadgeSize.Large,
          bordered = true,
          extraCls = if (neutral) "dark:text-body" else ""
        ).element.amend(
          styleAttr := "height: 1.5rem; padding: 0 0.25rem; display: inline-flex; align-items: center;"
        ),
        span(cls("text-sm text-body-subtle"), s"${group.tasks.size} ${if (group.tasks.size == 1) "task" else "tasks"}")
      ),
      div(cls("px-3 py-1 divide-y divide-default-medium"), group.tasks.map(taskRow))
    )
  }
}
