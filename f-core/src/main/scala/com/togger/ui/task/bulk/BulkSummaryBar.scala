package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor}
import com.togger.ui.task.TgDateFormater

import java.time.LocalDate

/** The numbers under both panels. Rendering only: what is counted lives in [[BulkSummary]]. */
object BulkSummaryBar {

  private val Empty = "—"
  private val dateFormater = new TgDateFormater

  def element(
    summary: Signal[BulkSummary],
    lookups: Signal[BulkLookups],
    warnings: Signal[Int],
    projectName: Signal[Option[String]]
  ): HtmlElement =
    div(
      cls("mt-4 pt-3 border-t border-default-medium grid grid-cols-2 md:grid-cols-4 gap-x-4 gap-y-3"),
      stat("Tasks", summary.map(_.taskCount.toString)),
      stat("Days of work", summary.map(_.workDays.toString)),
      stat("Earliest start", summary.map(current => date(current.earliestStart))),
      stat("Latest start", summary.map(current => date(current.latestStart))),
      stat("Last due date", summary.map(current => date(current.lastDueDate))),
      stat("Calendar span", summary.map(_.calendarSpanDays.fold(Empty)(days => s"$days days"))),
      stat("Without assignee", summary.map(_.unassigned.toString)),
      stat("Labels used", summary.map(_.labelsUsed.toString)),
      statElement("By priority", child <-- summary.map(current => badges(priorityBadges(current)))),
      statElement(
        "By status",
        child <-- summary.combineWith(lookups).map { case (current, currentLookups) =>
          badges(statusBadges(current, currentLookups))
        }
      ),
      stat("Project", projectName.map(_.getOrElse(Empty))),
      statElement(
        "Parse warnings",
        span(
          cls("text-sm font-medium"),
          cls <-- warnings.map(count => if (count > 0) "text-fg-danger-strong" else "text-heading"),
          child.text <-- warnings.map(_.toString)
        )
      )
    )

  private def date(day: Option[LocalDate]): String = day.fold(Empty)(dateFormater.asAmericanDateWithDayAbrv)

  private def stat(title: String, value: Signal[String]): HtmlElement =
    statElement(title, span(cls("text-sm font-medium text-heading"), child.text <-- value))

  private def statElement(title: String, value: Modifier[HtmlElement]): HtmlElement =
    div(cls("flex flex-col gap-0.5"), span(cls("text-xs text-body-subtle"), title), value)

  private def badges(specs: Seq[BadgeSpec]): HtmlElement =
    if (specs.isEmpty) span(cls("text-sm font-medium text-heading"), Empty)
    else div(cls("flex flex-wrap gap-1"), specs.map(spec => TgBadge(spec.text, spec.color).element))

  private def priorityBadges(summary: BulkSummary): Seq[BadgeSpec] =
    summary.byPriority.map { case (priority, count) =>
      BadgeSpec(s"${priority.shortCode} × $count", priority.badgeColor)
    }

  private def statusBadges(summary: BulkSummary, lookups: BulkLookups): Seq[BadgeSpec] =
    summary.byStatus.map { case (statusId, count) =>
      statusId.flatMap(id => lookups.statuses.find(_.id == id)) match {
        case Some(status) => BadgeSpec(s"${EmojiPrefix.of(status.name)} × $count", status.badgeColor)
        case None         => BadgeSpec(s"${EmojiPrefix.Fallback} × $count", TgBadgeColor.Gray)
      }
    }
}
