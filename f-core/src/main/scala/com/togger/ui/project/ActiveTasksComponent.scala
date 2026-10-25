package com.togger.ui.project

import com.raquo.laminar.api.L._
import com.togger.common.TgClock
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull}
import com.togger.routes.Pages.EditTaskPage
import com.togger.routes.{FrontendPage, TgRouter}
import com.togger.task.DefaultTaskStatus
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.task.bulk.BulkTaskCard

import java.time.DayOfWeek
import java.util.UUID

/** What a place showing the active tasks leaves out or says.
  *
  * @param emptyNote
  *   shown when there is no task being worked on
  * @param showMilestone
  *   the milestone badge of a task, left out where the milestone is already known
  * @param showMember
  *   a column with a row for each member, left out where the tasks are of one member
  */
case class ActiveTasksConfig(emptyNote: String, showMilestone: Boolean = true, showMember: Boolean = true)

/** The tasks being worked on in a column for each of the statuses of the daily, in its order, also when no task is in
  * one. With the member column, a row for each member with the tasks of the member in the column of their status, the
  * tasks without an assignee in the last row.
  */
class ActiveTasksComponent(
  router: TgRouter[FrontendPage],
  tgDateFormater: TgDateFormater,
  tgClock: TgClock,
  projectId: UUID,
  milestones: Seq[FindMilestoneResp],
  workDays: Set[DayOfWeek],
  tasks: Seq[FindTaskRespFull],
  config: ActiveTasksConfig
) {

  private val cellCls = "border-t border-default pt-2"

  private val columnStatuses = ActiveTasksComponent.ColumnStatuses

  private def taskItem(task: FindTaskRespFull): HtmlElement =
    li(
      cls("mb-1"),
      div(
        cls("flex flex-wrap items-center gap-2"),
        a(
          cls("text-fg-brand hover:underline cursor-pointer"),
          task.name,
          router.navigateTo(EditTaskPage(projectId, task.id))
        ),
        BulkTaskCard.readOnlyRow(ProjectHomeStats.labelBadges(task))
      ),
      BulkTaskCard.readOnlyRow(
        ProjectHomeStats.taskBadges(
          task,
          if (config.showMilestone) milestones else Seq.empty,
          showAssignee = false,
          tgClock.currentDate(),
          workDays,
          tgDateFormater.asAmericanDate
        )
      )
    )

  private def cell(columnTasks: Seq[FindTaskRespFull]): HtmlElement =
    div(cls(cellCls), ul(columnTasks.map(taskItem)))

  private def header(counts: Seq[Int]): Seq[HtmlElement] =
    columnStatuses.zip(counts).map { case (columnStatus, count) =>
      div(TgBadge(text = s"${columnStatus.toStringWithEmoji} ($count)", color = TgBadgeColor.Gray, bordered = true).element)
    }

  private def withMembers: HtmlElement = {
    val rows = ProjectHomeStats.memberRows(tasks)
    grid(
      "grid-cols-[auto_repeat(4,minmax(0,1fr))]",
      div(cls("font-semibold text-heading"), "Member") +:
        (header(columnStatuses.indices.map(column => rows.map(_._2(column).size).sum)) ++
          rows.flatMap { case (member, cells) =>
            div(cls(cellCls), cls("font-medium text-heading whitespace-nowrap"), member.fold("Unassigned")(_.fullName)) +:
              cells.map(cell)
          })
    )
  }

  private def withoutMembers: HtmlElement = {
    val cells = ProjectHomeStats.byColumn(tasks)
    grid("grid-cols-[repeat(4,minmax(0,1fr))]", header(cells.map(_.size)) ++ cells.map(cell))
  }

  private def grid(columnsCls: String, content: Seq[HtmlElement]): HtmlElement =
    div(cls("overflow-x-auto"), div(cls("grid gap-4 min-w-[48rem]"), cls(columnsCls), content))

  val element: HtmlElement =
    if (ProjectHomeStats.byColumn(tasks).forall(_.isEmpty)) div(cls("text-body"), config.emptyNote)
    else if (config.showMember) withMembers
    else withoutMembers
}

object ActiveTasksComponent {

  val ColumnStatuses: Seq[DefaultTaskStatus] =
    Seq(DefaultTaskStatus.InProgress, DefaultTaskStatus.Testing, DefaultTaskStatus.Waiting, DefaultTaskStatus.Blocked)
}
