package com.togger.ui.milestone

import com.togger.milestone.MilestoneStatus

/** How the progress of the steps of a milestone is told in the milestone screens. */
object MilestoneStepProgress {

  def label(total: Int, closed: Int): String =
    if (total == 0) "No steps" else s"$closed/$total steps closed"

  /** The steps still open on a milestone that is Done: it was closed before its plan was carried out. */
  def unfinishedWhenDone(status: MilestoneStatus, total: Int, closed: Int): Int =
    if (status == MilestoneStatus.Done) (total - closed).max(0) else 0

  def closedEarlyLabel(unfinished: Int): String =
    s"Closed with $unfinished unfinished ${if (unfinished == 1) "step" else "steps"}"

  /** The warning shown when a milestone is about to be closed with open steps; nothing when it has none. */
  def closeWarning(total: Int, closed: Int): Option[String] = {
    val open = (total - closed).max(0)
    Option.when(open > 0)(
      s"$open of $total ${if (total == 1) "step is" else "steps are"} not closed yet. " +
        "The milestone can still be closed, but it will show as closed with unfinished steps."
    )
  }
}
