package com.togger.ui.task.bulk

import com.raquo.laminar.api.L._
import com.togger.ui.components.TgBadge

import java.util.UUID

object BulkTaskCard {

  /** @param leading
    *   the selection checkbox, owned by the caller since selection spans the whole list
    * @param controls
    *   the reorder handle and buttons, owned by the caller since reordering is the list's business
    * @param rootModifiers
    *   drag-and-drop wiring and drop indicators for the card's root element
    */
  def element(
    key: UUID,
    task: Signal[BulkTask],
    lookups: Signal[BulkLookups],
    popups: BulkEditPopups,
    leading: HtmlElement,
    controls: HtmlElement,
    rootModifiers: Modifier[HtmlElement]*
  ): HtmlElement = {
    val badges = task.combineWith(lookups).map { case (current, currentLookups) =>
      BulkTaskBadges.of(current, currentLookups)
    }
    div(
      cls("rounded-base border border-default-medium bg-neutral-primary p-2.5"),
      rootModifiers,
      div(
        cls("mb-1.5 flex items-start gap-2"),
        leading,
        div(
          cls("grow flex flex-wrap items-center gap-x-2 gap-y-1"),
          p(cls("text-sm font-medium text-heading break-words"), child.text <-- task.map(_.name)),
          div(
            cls("flex flex-wrap items-center gap-1.5"),
            children <-- badges.map(rows => row(key, rows.labelRow, popups))
          )
        ),
        controls
      ),
      div(
        cls("flex flex-col gap-1"),
        children <-- badges.map(
          _.rows.map(specs => div(cls("flex flex-wrap items-center gap-1.5"), row(key, specs, popups)))
        )
      )
    )
  }

  /** A badge that has an attribute is a button that edits that attribute of this task only. */
  def row(key: UUID, specs: Seq[BadgeSpec], popups: BulkEditPopups): Seq[HtmlElement] =
    BulkTaskBadges.grouped(specs).map { group =>
      group.head.kind match {
        case Some(kind) =>
          popups.anchor(
            Some(key),
            kind,
            button(
              `type`("button"),
              cls("flex flex-wrap items-center gap-1.5 rounded-base cursor-pointer hover:ring-2 hover:ring-blue-300"),
              group.map(badge)
            )
          )
        case None => div(cls("flex items-center gap-1.5"), group.map(badge))
      }
    }

  private def badge(spec: BadgeSpec): HtmlElement = TgBadge(text = spec.text, color = spec.color).element
}
