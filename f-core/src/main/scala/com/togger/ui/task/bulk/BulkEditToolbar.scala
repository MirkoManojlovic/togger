package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._

/** One button per attribute; each opens a popup under it that edits all the selected tasks. */
class BulkEditToolbar(popups: BulkEditPopups) {

  private def trigger(kind: BulkEditKind, text: String): HtmlElement =
    popups.anchor(
      None,
      kind,
      button(
        `type`("button"),
        cls("px-2.5 py-1 text-xs font-medium rounded-base border border-default-medium text-body"),
        cls("hover:bg-neutral-tertiary-medium cursor-pointer"),
        text
      )
    )

  val element: HtmlElement =
    div(
      cls("flex flex-wrap items-center gap-1.5"),
      trigger(BulkEditKind.Priority, "Priority"),
      trigger(BulkEditKind.Status, "Status"),
      trigger(BulkEditKind.Milestone, "Milestone"),
      trigger(BulkEditKind.Assignee, "Assignee"),
      trigger(BulkEditKind.Labels, "Labels"),
      trigger(BulkEditKind.StartDate, "Start date"),
      trigger(BulkEditKind.DueDate, "Due date")
    )
}
