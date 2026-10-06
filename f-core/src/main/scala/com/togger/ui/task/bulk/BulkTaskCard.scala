package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.ui.components.TgBadge

object BulkTaskCard {

  /** @param leading
    *   the selection checkbox, owned by the caller since selection spans the whole list
    * @param controls
    *   the reorder handle and buttons, owned by the caller since reordering is the list's business
    * @param rootModifiers
    *   drag-and-drop wiring and drop indicators for the card's root element
    */
  def element(
    task: Signal[BulkTask],
    lookups: Signal[BulkLookups],
    leading: HtmlElement,
    controls: HtmlElement,
    rootModifiers: Modifier[HtmlElement]*
  ): HtmlElement =
    div(
      cls("rounded-base border border-default-medium bg-neutral-primary p-2.5"),
      rootModifiers,
      div(
        cls("mb-1.5 flex items-start gap-2"),
        leading,
        p(cls("grow text-sm font-medium text-heading break-words"), child.text <-- task.map(_.name)),
        controls
      ),
      div(
        cls("flex flex-col gap-1"),
        children <-- task.combineWith(lookups).map { case (current, currentLookups) =>
          BulkTaskBadges
            .of(current, currentLookups)
            .rows
            .map(row => div(cls("flex flex-wrap items-center gap-1.5"), row.map(badge)))
        }
      )
    )

  private def badge(spec: BadgeSpec): HtmlElement = TgBadge(text = spec.text, color = spec.color).element
}
