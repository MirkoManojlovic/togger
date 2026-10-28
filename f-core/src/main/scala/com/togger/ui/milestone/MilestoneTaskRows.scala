package com.togger.ui.milestone

import com.raquo.laminar.api.L._
import com.togger.dto.{FindTaskRespFull, FindTaskStatusResp}
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.markdown.TaskProgress
import com.togger.ui.task.TaskProgressComponent
import com.togger.ui.timebox.{DaysProgress, TimeboxReviewStats}

import java.time.{DayOfWeek, LocalDate}

/** The task counts of a milestone with their progress bars: a row for the total first, then one for each team member;
  * the counts are on the left of a row and the bars on the right.
  */
object MilestoneTaskRows {

  private def stat(label: String, value: String, color: TgBadgeColor = TgBadgeColor.Gray): HtmlElement =
    div(
      cls("flex flex-col items-center gap-1 min-w-16"),
      TgBadge(text = label, color = color, bordered = true).element,
      span(cls("text-xl font-semibold text-heading"), value)
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

  private def counts(tasks: Seq[FindTaskRespFull], statuses: Seq[FindTaskStatusResp]): HtmlElement =
    div(
      cls("flex flex-col items-center gap-3 sm:pr-4 sm:border-r border-default"),
      totalStat(tasks),
      div(
        cls("flex flex-col gap-2 w-full"),
        statuses.flatMap { status =>
          Some(tasks.count(_.taskStatus.id == status.id)).filter(_ > 0).map(statusRow(status, _))
        }
      )
    )

  private def progressRing(
    title: String,
    progress: TaskProgress,
    label: TaskProgress => String,
    fillCls: String,
    trackCls: String
  ): HtmlElement =
    div(
      cls("flex flex-col items-center gap-2 text-center px-3 border-r border-default last:border-r-0"),
      span(cls("text-base font-semibold text-heading"), title),
      TaskProgressComponent(
        Val(progress),
        ringSizeCls = "w-12 h-12",
        labelOf = label,
        fillCls = fillCls,
        trackCls = trackCls
      ).element
    )

  /** One card for the tasks of the total or of a member: the counts beside the rings they add up to. The days left
    * belong to the milestone, so only the total shows them.
    */
  private def row(
    title: String,
    tasks: Seq[FindTaskRespFull],
    statuses: Seq[FindTaskStatusResp],
    days: Option[DaysProgress],
    workDays: Option[DaysProgress]
  ): HtmlElement = {
    val progress = TimeboxReviewStats.tasksProgress(tasks)
    div(
      cls("p-3 border border-default rounded-base"),
      div(cls("font-semibold text-heading mb-3"), title),
      div(
        cls("flex flex-col sm:flex-row sm:items-start gap-4"),
        counts(tasks, statuses),
        div(
          cls("grid grid-cols-3"),
          progressRing(
            "Tasks",
            TaskProgress(progress.done, progress.total),
            p => s"${p.total - p.checked} of ${p.total} tasks left to do",
            "text-green-500",
            "text-green-200 dark:text-green-900"
          ),
          days.map(d =>
            progressRing(
              "Due date",
              TaskProgress(d.elapsed, d.total),
              p => s"${p.total - p.checked} of ${p.total} days left until the due date",
              "text-blue-500",
              "text-blue-200 dark:text-blue-900"
            )
          ),
          workDays.map(d =>
            progressRing(
              "Work days",
              TaskProgress(d.elapsed, d.total),
              p => s"${p.checked} days worked, ${p.total - p.checked} days of work left",
              "text-purple-500",
              "text-purple-200 dark:text-purple-900"
            )
          )
        )
      )
    )
  }

  def apply(
    tasks: Seq[FindTaskRespFull],
    statuses: Seq[FindTaskStatusResp],
    days: DaysProgress,
    today: LocalDate,
    workDays: Set[DayOfWeek]
  ): HtmlElement = {
    val memberRows: Seq[HtmlElement] =
      TimeboxReviewStats.tasksPerMember(tasks).map { case (member, memberTasks) =>
        row(member.fold("Unassigned")(_.fullName), memberTasks, statuses, None, None)
      }
    div(
      cls("flex flex-col gap-4"),
      row("Total", tasks, statuses, Some(days), Some(TimeboxReviewStats.workDaysProgress(tasks, today, workDays))),
      memberRows
    )
  }
}
