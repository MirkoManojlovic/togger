package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.common.WorkDayCalendar
import com.togger.dto.{FindMilestoneResp, FindTaskRespFull}
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
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
      .combineWith(workDays, timebox)
      .map { case (tasks, days, range) => StagedSummary.of(tasks, days, range) }
      .distinct

  private def date(day: Option[LocalDate]): String = day.fold(Empty)(tgDateFormater.asAmericanDateWithDayAbrv)

  private def stat(title: String, value: Signal[String]): HtmlElement =
    statElement(title, span(cls("text-sm font-medium text-heading"), child.text <-- value))

  private def statElement(title: String, value: Modifier[HtmlElement]): HtmlElement =
    div(cls("flex flex-col gap-0.5"), span(cls("text-xs text-body-subtle"), title), value)

  private def badges(items: Seq[HtmlElement]): HtmlElement =
    if (items.isEmpty) span(cls("text-sm font-medium text-heading"), Empty)
    else div(cls("flex flex-col items-start gap-1"), items)

  private val summaryBar: HtmlElement =
    div(
      cls("grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-4"),
      stat("Tasks staged", summarySignal.map(_.taskCount.toString)),
      stat("Days of work", summarySignal.map(_.workDays.toString)),
      stat("Earliest start", summarySignal.map(current => date(current.earliestStart))),
      stat("Last due date", summarySignal.map(current => date(current.lastDueDate))),
      stat("Calendar span", summarySignal.map(_.calendarSpanDays.fold(Empty)(days => s"$days days"))),
      stat("Without assignee", summarySignal.map(_.unassigned.toString)),
      statElement(
        "Outside timebox dates",
        span(
          cls("text-sm font-medium"),
          cls <-- summarySignal.map(current =>
            if (current.outsideTimebox > 0) "text-fg-danger-strong" else "text-heading"
          ),
          child.text <-- summarySignal.map(_.outsideTimebox.toString)
        )
      ),
      statElement(
        "By priority",
        child <-- summarySignal.map(current =>
          badges(current.byPriority.map { case (priority, count) =>
            TgBadge(s"${priority.shortCode} × $count", priority.badgeColor).element
          })
        )
      ),
      statElement(
        "By label",
        child <-- summarySignal.map(current =>
          badges(current.byLabel.map { case (label, count) =>
            TgBadge(s"${label.name} × $count", label.badgeColor).element
          })
        )
      ),
      statElement(
        "By status",
        child <-- summarySignal.map(current =>
          badges(current.byStatus.map { case (status, count) =>
            TgBadge(s"${status.name} × $count", status.badgeColor).element
          })
        )
      )
    )

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
      cls("flex cursor-pointer flex-wrap items-center gap-x-2 gap-y-1 rounded-base px-3 py-2"),
      cls("border border-default hover:bg-neutral-tertiary-medium"),
      title("Click to unstage"),
      span(cls("text-sm font-semibold text-heading"), task.name),
      milestoneBadge(task, all),
      TgBadge(task.priority.shortCode, task.priority.badgeColor).element,
      TgBadge(task.taskStatus.name, task.taskStatus.badgeColor).element,
      assigneeBadge(task),
      TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(task.startDate), TgBadgeColor.Gray).element,
      TgBadge(tgDateFormater.asAmericanDateWithDayAbrv(task.dueDate), TgBadgeColor.Gray).element,
      durationBadge(task, days),
      task.taskLabels.sortBy(_.name).map(label => TgBadge(label.name, label.badgeColor).element),
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

  private val stagedList: HtmlElement =
    div(
      cls("flex flex-col gap-2"),
      children <-- stagingStore
        .staged(projectId)
        .combineWith(milestones, workDays)
        .map { case (tasks, all, days) => tasks.map(stagedRow(_, all, days)) },
      div(
        cls("text-sm text-body"),
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
