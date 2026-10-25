package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.markdown.TaskProgress
import com.togger.ui.task.TaskProgressComponent
import com.togger.ui.timebox.{DaysProgress, TimeboxReviewStats}

/** The task counts of a milestone with their progress bars: a row for the total first, then one for each team member;
  * the counts are on the left of a row and the bars on the right.
  */
object MilestoneTaskRows {

  private def stat(label: String, value: String, color: TgBadgeColor = TgBadgeColor.Gray): HtmlElement =
    div(
      cls("flex flex-col items-center gap-1 min-w-16"),
      span(cls("text-xl font-semibold text-heading"), value),
      TgBadge(text = label, color = color, bordered = true).element
    )

  /** The tasks and how many of them are done, e.g. "12/5". */
  private def totalStat(tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    stat("Total / Done", s"${progress.total} / ${progress.done}")
  }

  private def statusRow(status: FindTaskStatusResp, count: Int): HtmlElement =
    div(
      cls("flex items-center justify-between gap-3 w-full"),
      TgBadge(text = status.badgeText, color = status.badgeColor, bordered = true).element,
      span(cls("text-lg font-semibold text-heading"), count.toString)
    )

  private def column(title: String, tasks: Seq[FindTaskRespFull], statuses: Seq[FindTaskStatusResp]): HtmlElement =
    div(
      cls("flex flex-col items-center gap-3 p-3 border border-default rounded-base"),
      div(cls("font-semibold text-heading"), title),
      totalStat(tasks),
      div(
        cls("flex flex-col gap-2 w-full"),
        statuses.flatMap { status =>
          Some(tasks.count(_.taskStatus.id == status.id)).filter(_ > 0).map(statusRow(status, _))
        }
      )
    )

  private def progressRing(progress: TaskProgress, label: TaskProgress => String): HtmlElement =
    TaskProgressComponent(
      Val(progress),
      ringSizeCls = "w-12 h-12",
      labelOf = label,
      fillCls = "text-green-500",
      trackCls = "text-green-200 dark:text-green-900"
    ).element

  private def row(
    title: String,
    tasks: Seq[FindTaskRespFull],
    statuses: Seq[FindTaskStatusResp],
    days: DaysProgress
  ): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    div(
      cls("flex flex-col md:flex-row md:items-start gap-4"),
      div(cls("md:w-1/2"), column(title, tasks, statuses).amend(cls("w-fit"))),
      div(
        cls("flex flex-col gap-3 md:w-1/2 p-3 border border-default rounded-base"),
        progressRing(TaskProgress(progress.done, progress.total), p => s"${p.total - p.checked} of ${p.total} tasks left to do"),
        progressRing(
          TaskProgress(days.elapsed, days.total),
          p => s"${p.total - p.checked} of ${p.total} days left until the due date"
        )
      )
    )
  }

  def apply(tasks: Seq[FindTaskRespFull], statuses: Seq[FindTaskStatusResp], days: DaysProgress): HtmlElement = {
    val memberRows: Seq[HtmlElement] =
      TimeboxReviewStats.tasksPerMember(tasks).map { case (member, memberTasks) =>
        row(member.fold("Unassigned")(_.fullName), memberTasks, statuses, days)
      }
    div(
      cls("flex flex-col gap-4"),
      row("Total", tasks, statuses, days),
      memberRows
    )
  }
}
