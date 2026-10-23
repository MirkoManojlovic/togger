package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.ui.components.TaskLabelBadgeColor.TaskLabelBadgeColorOps
import com.togger.ui.components.TaskPriorityBadgeColor.TaskPriorityBadgeColorOps
import com.togger.ui.components.TaskStatusBadgeColor.TaskStatusBadgeColorOps
import com.togger.ui.components.{TgBadge, TgBadgeColor, TgStatColumn}

/** The numbers under both panels. Rendering only: what is counted lives in [[BulkSummary]]. */
object BulkSummaryBar {

  private val Empty = "—"

  def element(
    summary: Signal[BulkSummary],
    lookups: Signal[BulkLookups],
    warnings: Signal[Int]
  ): HtmlElement =
    div(
      cls("mt-4 flex flex-col gap-4 border-t border-default-medium pt-3"),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        TgStatColumn(stat("Tasks", summary.map(_.taskCount.toString))),
        TgStatColumn(stat("Days of work", summary.map(_.workDays.toString))),
        TgStatColumn(stat("Without assignee", summary.map(_.unassigned.toString))),
        TgStatColumn(
          statElement(
            "Parse warnings",
            span(
              cls("text-sm font-medium"),
              cls <-- warnings.map(count => if (count > 0) "text-fg-danger-strong" else "text-heading"),
              child.text <-- warnings.map(_.toString)
            )
          )
        )
      ),
      div(
        cls("flex flex-wrap gap-x-4 gap-y-3"),
        TgStatColumn(statElement("By priority", child <-- summary.map(current => badges(priorityBadges(current))))),
        TgStatColumn(
          statElement(
            "By label",
            child <-- summary.combineWith(lookups).map { case (current, currentLookups) =>
              badges(labelBadges(current, currentLookups))
            }
          )
        ),
        TgStatColumn(
          statElement(
            "By status",
            child <-- summary.combineWith(lookups).map { case (current, currentLookups) =>
              badges(statusBadges(current, currentLookups))
            }
          )
        ),
        TgStatColumn(
          statElement(
            "By milestones",
            child <-- summary.combineWith(lookups).map { case (current, currentLookups) =>
              badges(milestoneBadges(current, currentLookups))
            }
          )
        )
      )
    )

  private def stat(title: String, value: Signal[String]): HtmlElement =
    statElement(title, span(cls("text-sm font-medium text-heading"), child.text <-- value))

  private def statElement(title: String, value: Modifier[HtmlElement]): HtmlElement =
    div(cls("flex flex-col gap-0.5"), span(cls("text-xs text-body-subtle"), title), value)

  private def badges(specs: Seq[BadgeSpec]): HtmlElement =
    if (specs.isEmpty) span(cls("text-sm font-medium text-heading"), Empty)
    else div(cls("flex flex-col items-center gap-1"), specs.map(spec => TgBadge(spec.text, spec.color).element))

  private def priorityBadges(summary: BulkSummary): Seq[BadgeSpec] =
    summary.byPriority.map { case (priority, count) =>
      BadgeSpec(s"${priority.shortCode} × $count", priority.badgeColor)
    }

  private def labelBadges(summary: BulkSummary, lookups: BulkLookups): Seq[BadgeSpec] =
    summary.byLabel
      .flatMap { case (labelId, count) => lookups.labels.find(_.id == labelId).map(_ -> count) }
      .sortBy { case (label, count) => (-count, label.name) }
      .map { case (label, count) => BadgeSpec(s"${label.name} × $count", label.badgeColor) }

  private def milestoneBadges(summary: BulkSummary, lookups: BulkLookups): Seq[BadgeSpec] =
    summary.byMilestone
      .flatMap { case (milestoneId, count) => lookups.milestones.find(_.id == milestoneId).map(_ -> count) }
      .sortBy { case (milestone, count) => (-count, milestone.name) }
      .map { case (milestone, count) => BadgeSpec(s"${milestone.name} × $count", TgBadgeColor.Alternative) }

  private def statusBadges(summary: BulkSummary, lookups: BulkLookups): Seq[BadgeSpec] =
    summary.byStatus.map { case (statusId, count) =>
      statusId.flatMap(id => lookups.statuses.find(_.id == id)) match {
        case Some(status) => BadgeSpec(s"${status.emoji} × $count", status.badgeColor)
        case None         => BadgeSpec(s"${EmojiPrefix.Fallback} × $count", TgBadgeColor.Gray)
      }
    }
}
