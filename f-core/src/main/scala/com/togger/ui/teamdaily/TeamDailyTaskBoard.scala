package com.togger.ui.teamdaily

import com.raquo.laminar.api.L._
import com.togger.dto.FindMilestoneResp
import com.togger.dto.FindMilestoneStepResp
import com.togger.ui.components.TaskStatusBadgeColor
import com.togger.ui.components.TgBadge
import com.togger.ui.task.TaskProgressComponent
import com.togger.ui.task.bulk.BulkTaskCard

/** The task picker as a board like the active tasks of the project home: a column for each status, in the order of the
  * daily. With members, a row for each member holds their tasks in the column of their status.
  */
object TeamDailyTaskBoard {

  private val cellCls = "border-t border-default pt-2"

  private val columnStatuses = TeamDailyStatusOrder.Ordered

  /** The tasks by column, each by name; tasks without a column are left out. */
  def byColumn(tasks: Seq[TeamDailyTaskPlan]): Seq[Seq[TeamDailyTaskPlan]] = {
    val ranked = tasks.flatMap(task => TeamDailyStatusOrder.rank(task.statusName).map(_ -> task))
    columnStatuses.indices.map(column => ranked.collect { case (rank, task) if rank == column => task }.sortBy(_.name))
  }

  private def header(counts: Seq[Int]): Seq[HtmlElement] =
    columnStatuses.zip(counts).map { case (status, count) =>
      div(
        cls("flex justify-center"),
        TgBadge(
          text = s"${status.toStringWithEmoji} ($count)",
          color = TaskStatusBadgeColor.of(status.statusName),
          bordered = true
        ).element
      )
    }

  private def grid(columnsCls: String, content: Seq[HtmlElement]): HtmlElement =
    div(cls("overflow-x-auto"), div(cls("grid gap-4 min-w-[72rem]"), cls(columnsCls), content))

  private def cell(cards: Seq[HtmlElement]): HtmlElement = div(cls(cellCls), cards)

  /** The tasks of one person in a column for each status. */
  def apply(tasks: Seq[TeamDailyTaskPlan], card: TeamDailyTaskPlan => HtmlElement): HtmlElement = {
    val columns = byColumn(tasks)
    grid("grid-cols-[repeat(6,minmax(0,1fr))]", header(columns.map(_.size)) ++ columns.map(_.map(card)).map(cell))
  }

  /** A row for each member: what names the member in the first column, and the tasks in a column for each status. */
  def withMembers(
    rows: Seq[(HtmlElement, Seq[TeamDailyTaskPlan])],
    card: TeamDailyTaskPlan => HtmlElement
  ): HtmlElement = {
    val byMember = rows.map { case (member, tasks) => member -> byColumn(tasks) }
    grid(
      "grid-cols-[auto_repeat(6,minmax(0,1fr))]",
      div(cls("flex items-center justify-center text-sm font-medium text-heading"), "Member") +:
        (header(columnStatuses.indices.map(column => byMember.map(_._2(column).size).sum)) ++
          byMember.flatMap { case (member, columns) =>
            div(cls(cellCls), cls("flex"), styleAttr := "max-width: 11rem;", member) +: columns
              .map(_.map(card))
              .map(cell)
          })
    )
  }

  /** A task to check: name, labels and progress, then milestone, step and priority, then a note; dimmed when it is not
    * chosen.
    */
  def card(
    task: TeamDailyTaskPlan,
    checkbox: HtmlElement,
    selected: Signal[Boolean],
    note: String,
    milestones: Seq[FindMilestoneResp],
    steps: Seq[FindMilestoneStepResp]
  ): HtmlElement =
    label(
      cls("flex items-start gap-2 mb-2 p-2 border border-default rounded-base bg-neutral-primary-soft"),
      cls("text-sm text-body cursor-pointer hover:bg-neutral-tertiary"),
      cls("opacity-50") <-- selected.map(!_),
      checkbox.amend(cls("mt-0.5 shrink-0")),
      div(
        cls("flex min-w-0 flex-1 flex-col gap-1"),
        div(
          cls("flex flex-wrap items-center gap-2"),
          span(cls("font-medium text-black dark:text-white"), task.name),
          BulkTaskCard.readOnlyRow(TeamDailyTaskBadges.labels(task)),
          TaskProgressComponent
            .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
            .element
            .amend(cls("self-center leading-none"))
        ),
        BulkTaskCard.readOnlyRow(TeamDailyTaskBadges.milestoneAndPriority(task, milestones, steps)),
        span(cls("text-xs text-body-subtle"), note)
      )
    )
}
