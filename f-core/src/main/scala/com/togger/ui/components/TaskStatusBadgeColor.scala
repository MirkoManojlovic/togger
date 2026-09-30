package com.togger.ui.components

import com.togger.dto.FindTaskStatusResp
import com.togger.task.DefaultTaskStatus

/** Maps a task's status name onto the fixed [[TgBadgeColor]] palette (so it keeps working in dark mode), matching it
  * against [[DefaultTaskStatus]] by name since a project's task statuses are stored as free-form rows rather than
  * this enum. An unrecognized status name falls back to [[TgBadgeColor.Gray]].
  */
object TaskStatusBadgeColor {

  private val colorByStatus: Map[DefaultTaskStatus, TgBadgeColor] = Map(
    DefaultTaskStatus.InProgress -> TgBadgeColor.Blue,
    DefaultTaskStatus.Testing -> TgBadgeColor.Blue,
    DefaultTaskStatus.Waiting -> TgBadgeColor.Yellow,
    DefaultTaskStatus.Blocked -> TgBadgeColor.Red,
    DefaultTaskStatus.Todo -> TgBadgeColor.Gray,
    DefaultTaskStatus.Backlog -> TgBadgeColor.Gray,
    DefaultTaskStatus.Done -> TgBadgeColor.Green,
    DefaultTaskStatus.WontDo -> TgBadgeColor.Dark,
    DefaultTaskStatus.Archived -> TgBadgeColor.Dark
  )

  def of(statusName: String): TgBadgeColor =
    DefaultTaskStatus.values
      .find(_.statusName == statusName)
      .flatMap(colorByStatus.get)
      .getOrElse(TgBadgeColor.Gray)

  /** Lets callers write `taskStatus.badgeColor` instead of `TaskStatusBadgeColor.of(taskStatus.name)`. */
  implicit class TaskStatusBadgeColorOps(private val taskStatus: FindTaskStatusResp) extends AnyVal {
    def badgeColor: TgBadgeColor = of(taskStatus.name)
  }

}
