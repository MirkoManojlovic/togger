package com.togger.ui.timebox

import com.raquo.laminar.api.L._
import com.togger.components.button.TgHtmlButton
import com.togger.dto.FindTaskRespFull
import com.togger.ui.auth.TgCard
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.task.TgDateFormater

import java.time.{DayOfWeek, LocalDate}
import java.util.UUID

/** The tasks staged for the timebox, in a card of their own below the task table: the tasks themselves, then what they
  * add up to. A click on a staged task unstages it.
  */
class StagedTasksComponent(
  stagingStore: TimeboxStagingStore,
  projectId: UUID,
  workDays: Signal[Set[DayOfWeek]],
  timebox: Signal[Option[(LocalDate, LocalDate)]],
  tgDateFormater: TgDateFormater,
  capacity: Signal[Option[TimeboxCapacity]]
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
    else div(cls("flex flex-wrap gap-1"), items)

  private val summaryBar: HtmlElement =
    div(
      cls("grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-4"),
      stat("Tasks staged", summarySignal.map(_.taskCount.toString)),
      stat("Days of work", summarySignal.map(_.workDays.toString)),
      stat("Earliest start", summarySignal.map(current => date(current.earliestStart))),
      stat("Last due date", summarySignal.map(current => date(current.lastDueDate))),
      stat("Calendar span", summarySignal.map(_.calendarSpanDays.fold(Empty)(days => s"$days days"))),
      stat("Without assignee", summarySignal.map(_.unassigned.toString)),
      stat("Labels used", summarySignal.map(_.labelsUsed.toString)),
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
        "By status",
        child <-- summarySignal.map(current =>
          badges(current.byStatus.map { case (status, count) =>
            TgBadge(s"${status.name} × $count", status.badgeColor).element
          })
        )
      )
    )

  private def stagedRow(task: FindTaskRespFull): HtmlElement =
    div(
      cls("flex cursor-pointer flex-wrap items-center gap-x-2 gap-y-1 rounded-base px-3 py-2"),
      cls("border border-default hover:bg-neutral-tertiary-medium"),
      title("Click to unstage"),
      span(cls("text-sm font-semibold text-heading"), task.name),
      task.taskLabels.sortBy(_.name).map(label => TgBadge(label.name, label.badgeColor).element),
      TgBadge(task.priority.shortCode, task.priority.badgeColor).element,
      TgBadge(task.taskStatus.name, task.taskStatus.badgeColor).element,
      TgBadge(
        s"${tgDateFormater.asAmericanDateWithDayAbrv(task.startDate)} - ${tgDateFormater.asAmericanDateWithDayAbrv(task.dueDate)}",
        TgBadgeColor.Gray
      ).element,
      onClick.mapToUnit --> (_ => stagingStore.unstage(projectId, task.id))
    )

  private val clearAllButton: HtmlElement =
    div(
      cls("mb-2 flex justify-end"),
      cls("hidden") <-- stagingStore.staged(projectId).map(_.isEmpty),
      TgHtmlButton
        .extraSmall("Clear all")
        .amend(typ("button"), onClick.mapToUnit --> (_ => stagingStore.clear(projectId)))
    )

  private val stagedList: HtmlElement =
    div(
      cls("flex flex-col gap-2"),
      children <-- stagingStore.staged(projectId).map(_.map(stagedRow)),
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
            clearAllButton,
            stagedList,
            summaryBar.amend(cls("mt-4 border-t border-default-medium pt-3")),
            new TimeboxCapacityComponent(capacity).element.amend(cls("mt-4 border-t border-default-medium pt-3"))
          )
        )
      )
      .element
}
