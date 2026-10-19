package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.{FindMilestoneResp, FindTaskLabelResp, FindTaskRespFull, FindTaskStatusResp}
import com.togger.task.TaskPriority
import com.togger.ui.task.TaskProgressComponent
import com.togger.ui.apexchart.ApexChartComponent
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgStatColumn, TgTab, TgTabs}
import com.togger.ui.task.TgDateFormater

import org.scalajs.dom

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The badges of a staged task as three rows, each of which can edit what its badges show. */
case class StagedTaskBadges(labels: HtmlElement, primary: HtmlElement, dates: HtmlElement)

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
  milestones: Signal[Seq[FindMilestoneResp]],
  isDark: Signal[Boolean],
  taskBadges: FindTaskRespFull => StagedTaskBadges
) {

  private val Empty = "—"

  private val summarySignal: Signal[StagedSummary] =
    stagingStore
      .stagedWithDates(projectId)
      .combineWith(workDays, timebox, milestones)
      .map { case (tasks, days, range, all) => StagedSummary.of(tasks, days, range, all) }
      .distinct

  private def date(day: Option[LocalDate]): String = day.fold(Empty)(tgDateFormater.asAmericanDateWithDayAbrv)

  private def stat(title: String, value: Signal[String]): HtmlElement =
    statElement(title, span(cls("text-sm font-medium text-heading"), child.text <-- value))

  private def statElement(title: String, value: Modifier[HtmlElement]): HtmlElement =
    div(cls("flex flex-col gap-0.5"), span(cls("text-xs text-body-subtle"), title), value)

  private def badges(items: Seq[HtmlElement], alignment: String = "items-start"): HtmlElement =
    if (items.isEmpty) span(cls("text-sm font-medium text-heading"), Empty)
    else div(cls(s"flex flex-col $alignment gap-1"), items)

  private def badgeColumn[A](counts: Signal[Seq[(A, Int)]])(badge: (A, Int) => HtmlElement): Modifier[HtmlElement] =
    child <-- counts.map(items =>
      badges(items.map { case (item, count) => badge(item, count) }, "items-center")
    )

  private def priorityBadge(priority: TaskPriority, count: Int): HtmlElement =
    TgBadge(s"${priority.shortCode} × $count", priority.badgeColor).element

  private def labelBadge(label: FindTaskLabelResp, count: Int): HtmlElement =
    TgBadge(s"${label.name} × $count", label.badgeColor).element

  private def statusBadge(status: FindTaskStatusResp, count: Int): HtmlElement =
    TgBadge(s"${status.name} × $count", status.badgeColor).element

  private def assigneeCountBadge(assignee: String, count: Int): HtmlElement =
    TgBadge(s"$assignee × $count", TgBadgeColor.Gray).element

  private def milestoneCountBadge(milestone: FindMilestoneResp, count: Int): HtmlElement =
    TgBadge(s"${milestone.name} × $count", TgBadgeColor.Alternative).element

  /** The column a grouping is by is left out: every row in it would say the same. */
  private def groupBlock(grouping: StagedGrouping, summary: GroupSummary): HtmlElement =
    div(
      cls("flex flex-col gap-2 rounded-base border border-default px-3 py-2"),
      span(cls("text-sm font-semibold text-heading"), summary.title),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        TgStatColumn(stat("Tasks", Val(summary.taskCount.toString))),
        TgStatColumn(stat("Work days", Val(summary.workDays.toString)))
      ),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        Option.when(grouping != StagedGrouping.ByPriority)(
          TgStatColumn(
            statElement("Priorities", badges(summary.byPriority.map((priorityBadge _).tupled), "items-center"))
          )
        ),
        Option.when(grouping != StagedGrouping.ByLabel)(
          TgStatColumn(statElement("Labels", badges(summary.byLabel.map((labelBadge _).tupled), "items-center")))
        ),
        Option.when(grouping != StagedGrouping.ByStatus)(
          TgStatColumn(statElement("Status", badges(summary.byStatus.map((statusBadge _).tupled), "items-center")))
        ),
        Option.when(grouping != StagedGrouping.ByMilestone)(
          TgStatColumn(
            statElement(
              "Milestones",
              badges(summary.byMilestone.map((milestoneCountBadge _).tupled), "items-center")
            )
          )
        ),
        Option.when(grouping != StagedGrouping.ByAssignee)(
          TgStatColumn(
            statElement("Assignees", badges(summary.byAssignee.map((assigneeCountBadge _).tupled), "items-center"))
          )
        )
      )
    )

  private val totalsStats: HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        TgStatColumn(stat("Tasks staged", summarySignal.map(_.taskCount.toString))),
        TgStatColumn(stat("Days of work", summarySignal.map(_.workDays.toString)))
      ),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        TgStatColumn(statElement("By priority", badgeColumn(summarySignal.map(_.byPriority))(priorityBadge))),
        TgStatColumn(statElement("By label", badgeColumn(summarySignal.map(_.byLabel))(labelBadge))),
        TgStatColumn(statElement("By status", badgeColumn(summarySignal.map(_.byStatus))(statusBadge))),
        TgStatColumn(statElement("By milestones", badgeColumn(summarySignal.map(_.byMilestone))(milestoneCountBadge))),
        TgStatColumn(statElement("By assignee", badgeColumn(summarySignal.map(_.byAssignee))(assigneeCountBadge)))
      )
    )

  private def groupSummaries(grouping: StagedGrouping): Signal[Seq[GroupSummary]] =
    stagingStore
      .stagedWithDates(projectId)
      .combineWith(workDays, milestones)
      .map { case (tasks, days, all) => StagedSummary.grouped(grouping, tasks, days, all) }

  /** Tasks by group; the chart is only on the page while there are tasks, as it has nothing to draw otherwise. */
  private def groupPie(summaries: Signal[Seq[GroupSummary]]): HtmlElement = {
    val chart = new ApexChartComponent(summaries.map(StagedCharts.pieOfTaskCounts), isDark).element
    div(child <-- summaries.map(current => if (current.isEmpty) emptyNode else chart))
  }

  private def groupedStats(grouping: StagedGrouping): HtmlElement = {
    val summaries = groupSummaries(grouping)
    div(
      cls("flex flex-col gap-4"),
      div(
        cls("flex flex-col gap-2"),
        children <-- summaries.map(_.map(groupBlock(grouping, _)))
      ),
      Option.when(grouping != StagedGrouping.NotGrouped)(groupPie(summaries))
    )
  }

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

  private def isInteractive(target: dom.EventTarget): Boolean =
    target match {
      case element: dom.Element => element.closest("button, input, a") != null
      case _                    => false
    }

  private def stagedRow(task: FindTaskRespFull): HtmlElement = {
    val badges = taskBadges(task)
    div(
      cls("flex cursor-pointer flex-col gap-1 rounded-base px-3 py-2"),
      cls("border border-default hover:bg-neutral-tertiary-medium"),
      title("Click to unstage"),
      div(
        cls("flex flex-wrap items-center gap-x-2 gap-y-1"),
        span(cls("text-sm font-semibold text-heading"), task.name),
        badges.labels,
        TaskProgressComponent
          .fromDescription(Val(task.description), ringSizeCls = "w-3 h-3", textSizeCls = "text-xs")
          .element
      ),
      badges.primary,
      badges.dates,
      // A click on a badge, or in the popup it opens, edits the task and must not unstage it.
      onClick.filter(event => !isInteractive(event.target)).mapToUnit --> (_ => stagingStore.unstage(projectId, task.id))
    )
  }

  private val clearAllButton: HtmlElement =
    div(
      cls("mt-2 flex justify-end"),
      cls("hidden") <-- stagingStore.stagedWithDates(projectId).map(_.isEmpty),
      TgHtmlButton
        .extraSmall("Clear Stage")
        .amend(typ("button"), onClick.mapToUnit --> (_ => stagingStore.clear(projectId)))
    )

  private def groupSection(group: StagedGroup): HtmlElement =
    div(
      cls("flex flex-col gap-2"),
      if (group.title.isEmpty) emptyNode
      else
        span(cls("text-xs font-semibold uppercase text-body-subtle"), s"${group.title} (${group.tasks.size})"),
      group.tasks.map(stagedRow)
    )

  private def groupedList(grouping: StagedGrouping): HtmlElement =
    div(
      cls("flex flex-col gap-4"),
      children <-- stagingStore
        .stagedWithDates(projectId)
        .combineWith(milestones)
        .map { case (tasks, all) => grouping.group(tasks, all).map(groupSection) }
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
        cls("hidden") <-- stagingStore.stagedWithDates(projectId).map(_.nonEmpty)
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
