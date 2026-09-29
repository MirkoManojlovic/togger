package com.togger.ui.components

import com.togger.dto.FindTaskLabelResp
import com.togger.task.DefaultTaskLabel

/** Maps a task label's name onto the fixed [[TgBadgeColor]] palette (so it keeps working in dark mode), matching it
  * against [[DefaultTaskLabel]] by name since a project's task labels are stored as free-form rows rather than this
  * enum. An unrecognized label name falls back to [[TgBadgeColor.Gray]].
  */
object TaskLabelBadgeColor {

  private val colorByLabel: Map[DefaultTaskLabel, TgBadgeColor] = Map(
    DefaultTaskLabel.Feature -> TgBadgeColor.Green,
    DefaultTaskLabel.Bug -> TgBadgeColor.Red,
    DefaultTaskLabel.Refactor -> TgBadgeColor.Purple,
    DefaultTaskLabel.Test -> TgBadgeColor.Blue,
    DefaultTaskLabel.Docs -> TgBadgeColor.Cyan,
    DefaultTaskLabel.Chore -> TgBadgeColor.Gray
  )

  def of(labelName: String): TgBadgeColor =
    DefaultTaskLabel.values
      .find(_.labelName == labelName)
      .flatMap(colorByLabel.get)
      .getOrElse(TgBadgeColor.Gray)

  /** Lets callers write `taskLabel.badgeColor` instead of `TaskLabelBadgeColor.of(taskLabel.name)`. */
  implicit class TaskLabelBadgeColorOps(private val taskLabel: FindTaskLabelResp) extends AnyVal {
    def badgeColor: TgBadgeColor = of(taskLabel.name)
  }

}
