package com.togger.markdown

/** How many of a task's checkboxes are ticked.
  *
  * `total == 0` means the description has no task list at all, which a caller treats differently from "0 of 0
  * done" — there is no progress to show, not zero progress.
  */
case class TaskProgress(checked: Int, total: Int) {

  /** Whole-percent, rounded rather than floored, so `1 of 3` reads as 33% and not a truncated 30-odd. `0` for an
    * empty list rather than a division by zero.
    */
  def percent: Int = if (total == 0) 0 else Math.round(checked * 100f / total)
}

/** Counts a task's checkboxes from its markdown description.
  *
  * Delegates entirely to [[TgMarkdownTasks]] rather than re-scanning the text itself, so "what counts as a task
  * item" is defined in exactly one place — the same one the split editor's toggling and the renderer's detection
  * already agree on.
  */
object TaskProgressParser {

  def parse(description: String): TaskProgress = {
    val states = TgMarkdownTasks.markers(description).map(_.checked)
    TaskProgress(checked = states.count(identity), total = states.size)
  }
}
