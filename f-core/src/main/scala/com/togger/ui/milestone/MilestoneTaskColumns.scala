package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.timebox.TimeboxReviewStats

/** The task counts of a milestone in bordered columns: the total first, then one column for each team member. */
object MilestoneTaskColumns {

  private def stat(label: String, value: String, color: TgBadgeColor = TgBadgeColor.Gray): HtmlElement =
    div(
      cls("flex flex-col items-center gap-1 min-w-16"),
      span(cls("text-xl font-semibold text-heading"), value),
      TgBadge(text = label, color = color, bordered = true).element
    )

  /** The tasks and how many of them are done, e.g. "12/5". */
  private def totalStat(tasks: Seq[FindTaskRespFull]): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    stat("Total / Done", s"${progress.total}/${progress.done}")
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

  def apply(tasks: Seq[FindTaskRespFull], statuses: Seq[FindTaskStatusResp]): HtmlElement = {
    val memberColumns: Seq[HtmlElement] =
      TimeboxReviewStats.tasksPerMember(tasks).map { case (member, memberTasks) =>
        column(member.fold("Unassigned")(_.fullName), memberTasks, statuses)
      }
    div(
      cls("flex flex-wrap items-stretch gap-3"),
      column("Total", tasks, statuses),
      memberColumns
    )
  }
}
