package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp}
import com.togger.task.TaskPriority
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgTab, TgTabs}
import com.togger.ui.task.TgDateFormater
import com.togger.ui.task.bulk.BulkTaskBadges

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The tasks staged for the timebox, in a card of their own beside the task table: the capacity bars on top, then the
  * tasks themselves, then what they add up to. A click on a staged task unstages it.
  */
class StagedTasksComponent(
  stagingStore: TimeboxStagingStore,
  projectId: UUID,
  workDays: Signal[Set[DayOfWeek]],
  timebox: Signal[Option[(LocalDate, LocalDate)]],
  tgDateFormater: TgDateFormater,
  capacity: Signal[Option[TimeboxCapacity]],
  milestones: Signal[Seq[FindMilestoneResp]]
) {

  private val Empty = "—"

  private val summarySignal: Signal[StagedSummary] =
    stagingStore
      .staged(projectId)
      .combineWith(workDays, timebox, milestones)
      .map { case (tasks, days, range, all) => StagedSummary.of(tasks, days, range, all) }
      .distinct

  private def date(day: Option[LocalDate]): String = day.fold(Empty)(tgDateFormater.asAmericanDateWithDayAbrv)

  private def stat(title: String, value: Signal[String]): HtmlElement =
    statElement(title, span(cls("text-sm font-medium text-heading"), child.text <-- value))

  private def statElement(title: String, value: Modifier[HtmlElement]): HtmlElement =
    div(cls("flex flex-col gap-0.5"), span(cls("text-xs text-body-subtle"), title), value)

  private def badges(items: Seq[HtmlElement]): HtmlElement =
    if (items.isEmpty) span(cls("text-sm font-medium text-heading"), Empty)
    else div(cls("flex flex-col items-start gap-1"), items)

  private def badgeColumn[A](counts: Signal[Seq[(A, Int)]])(badge: (A, Int) => HtmlElement): Modifier[HtmlElement] =
    child <-- counts.map(items => badges(items.map { case (item, count) => badge(item, count) }))

  private def priorityBadge(priority: TaskPriority, count: Int): HtmlElement =
    TgBadge(s"${priority.shortCode} × $count", priority.badgeColor).element

  private def labelBadge(label: FindTaskLabelResp, count: Int): HtmlElement =
    TgBadge(s"${label.name} × $count", label.badgeColor).element

  private def statusBadge(status: FindTaskStatusResp, count: Int): HtmlElement =
    TgBadge(s"${status.name} × $count", status.badgeColor).element

  private def milestoneCountBadge(milestone: FindMilestoneResp, count: Int): HtmlElement =
    TgBadge(s"${milestone.name} × $count", TgBadgeColor.Alternative).element

  /** Columns share the row evenly, so a left out column leaves its space to the others. */
  private def column(element: HtmlElement): HtmlElement = element.amend(cls("min-w-32 flex-1"))

  /** The column a grouping is by is left out: every row in it would say the same. */
  private def groupBlock(grouping: StagedGrouping, summary: GroupSummary): HtmlElement =
    div(
      cls("flex flex-col gap-2 rounded-base border border-default px-3 py-2"),
      span(cls("text-sm font-semibold text-heading"), summary.title),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        column(stat("Tasks", Val(summary.taskCount.toString))),
        column(stat("Work days", Val(summary.workDays.toString)))
      ),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        Option.when(grouping != StagedGrouping.ByPriority)(
          column(statElement("Priorities", badges(summary.byPriority.map((priorityBadge _).tupled))))
        ),
        Option.when(grouping != StagedGrouping.ByLabel)(
          column(statElement("Labels", badges(summary.byLabel.map((labelBadge _).tupled))))
        ),
        Option.when(grouping != StagedGrouping.ByStatus)(
          column(statElement("Status", badges(summary.byStatus.map((statusBadge _).tupled))))
        ),
        Option.when(grouping != StagedGrouping.ByMilestone)(
          column(statElement("Milestones", badges(summary.byMilestone.map((milestoneCountBadge _).tupled))))
        )
      )
    )

  private val totalsStats: HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      div(
        cls("grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-3"),
        stat("Tasks staged", summarySignal.map(_.taskCount.toString)),
        stat("Calendar span", summarySignal.map(_.calendarSpanDays.fold(Empty)(days => s"$days days"))),
        stat("Days of work", summarySignal.map(_.workDays.toString))
      ),
      div(
        cls("grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-4"),
        statElement("By priority", badgeColumn(summarySignal.map(_.byPriority))(priorityBadge)),
        statElement("By label", badgeColumn(summarySignal.map(_.byLabel))(labelBadge)),
        statElement("By status", badgeColumn(summarySignal.map(_.byStatus))(statusBadge)),
        statElement("By milestones", badgeColumn(summarySignal.map(_.byMilestone))(milestoneCountBadge))
      )
    )

  private def groupedStats(grouping: StagedGrouping): HtmlElement =
    div(
      cls("flex flex-col gap-2"),
      children <-- stagingStore
        .staged(projectId)
        .combineWith(workDays, milestones)
        .map { case (tasks, days, all) => StagedSummary.grouped(grouping, tasks, days, all).map(groupBlock(grouping, _)) }
    )

  private val selectedStatsGroupingVar = Var(StagedGrouping.NotGrouped.id)

  private val summaryBar: HtmlElement =
    new TgTabs(
      StagedGrouping.all.map(grouping =>
        TgTab(
          grouping.id,
          grouping.label,
          if (grouping == StagedGrouping.NotGrouped) totalsStats else groupedStats(grouping)
        )
      ),
      selectedStatsGroupingVar
    ).element

  private def milestoneBadge(task: FindTaskRespFull, all: Seq[FindMilestoneResp]): HtmlElement =
    task.milestone
      .flatMap(id => all.find(_.id == id))
      .fold(TgBadge(BulkTaskBadges.EmptyMilestone, TgBadgeColor.Gray))(milestone =>
        TgBadge(milestone.name, TgBadgeColor.Alternative)
      )
      .element

  private def assigneeBadge(task: FindTaskRespFull): HtmlElement =
    TgBadge(task.assignedTo.fold(BulkTaskBadges.EmptyAssignee)(_.fullName), TgBadgeColor.Gray).element

  private def durationBadge(task: FindTaskRespFull, days: Set[DayOfWeek]): HtmlElement = {
    val count = WorkDayCalendar.countWorkDays(task.startDate, task.dueDate, days)
    TgBadge(if (count == 1) "1 day" else s"$count days", TgBadgeColor.Gray).element
  }

  private def stagedRow(task: FindTaskRespFull, all: Seq[FindMilestoneResp], days: Set[DayOfWeek]): HtmlElement =
    div(
      cls("flex cursor-pointer flex-col gap-1 rounded-base px-3 py-2"),
      cls("border border-default hover:bg-neutral-tertiary-medium"),
      title("Click to unstage"),
      div(
        cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
        span(cls("text-sm font-semibold text-heading"), task.name),
        task.taskLabels.sortBy(_.name).map(label => TgBadge(label.name, label.badgeColor).element)
      ),
      div(
        cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
        milestoneBadge(task, all),
        TgBadge(task.priority.shortCode, task.priority.badgeColor).element,
        TgBadge(task.taskStatus.name, task.taskStatus.badgeColor).element,
        assigneeBadge(task),
        TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(task.startDate), TgBadgeColor.Gray).element,
        TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(task.dueDate), TgBadgeColor.Gray).element,
        durationBadge(task, days)
      ),
      onClick.mapToUnit --> (_ => stagingStore.unstage(projectId, task.id))
    )

  private val clearAllButton: HtmlElement =
    div(
      cls("mt-2 flex justify-end"),
      cls("hidden") <-- stagingStore.staged(projectId).map(_.isEmpty),
      TgHtmlButton
        .extraSmall("Clear Stage")
        .amend(typ("button"), onClick.mapToUnit --> (_ => stagingStore.clear(projectId)))
    )

  private def groupSection(group: StagedGroup, all: Seq[FindMilestoneResp], days: Set[DayOfWeek]): HtmlElement =
    div(
      cls("flex flex-col gap-2"),
      if (group.title.isEmpty) emptyNode
      else
        span(cls("text-xs font-semibold uppercase text-body-subtle"), s"${group.title} (${group.tasks.size})"),
      group.tasks.map(stagedRow(_, all, days))
    )

  private def groupedList(grouping: StagedGrouping): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      children <-- stagingStore
        .staged(projectId)
        .combineWith(milestones, workDays)
        .map { case (tasks, all, days) => grouping.group(tasks, all).map(groupSection(_, all, days)) }
    )

  private val selectedGroupingVar = Var(StagedGrouping.NotGrouped.id)

  private val stagedList: HtmlElement =
    div(
      new TgTabs(
        StagedGrouping.all.map(grouping => TgTab(grouping.id, grouping.label, groupedList(grouping))),
        selectedGroupingVar
      ).element,
      div(
        cls("mt-2 text-sm text-body"),
        "No tasks staged. Click a task above to stage it.",
        cls("hidden") <-- stagingStore.staged(projectId).map(_.nonEmpty)
      )
    )

  val element: HtmlElement =
    TgCard
      .headerNoFooter(
        header = TgCard.CardHeader.plainText("Staged tasks", TgCard.CardHeaderSize.H5),
        body = TgCard.HtmlCardContent(
          div(
            new TimeboxCapacityComponent(capacity).element.amend(cls("mb-4 border-b border-default-medium pb-3")),
            stagedList,
            clearAllButton,
            summaryBar.amend(cls("mt-4 border-t border-default-medium pt-3"))
          )
        )
      )
      .element
}
