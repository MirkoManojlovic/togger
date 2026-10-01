package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.organization.TgIcons

import scala.scalajs.js

/** One row of a [[QuickOptionsPopup]]: a checkmark for the current pick, an optional colored badge in place of plain
  * text, and a one-line description.
  */
case class QuickOption(
  id: String,
  label: String,
  description: String,
  isSelected: Boolean,
  badgeColor: Option[TgBadgeColor] = None
)

/** The option-list popup a trigger char or a clicked badge opens, shared by
  * [[com.togger.ui.task.quick.CreateTaskQuick]] (where it first lived) and anything else that needs the same look.
  *
  * Row-highlighting is the caller's concern: `isHighlighted` is a function rather than a plain `Seq[Boolean]` so a
  * caller driving it from arrow-key state (as `CreateTaskQuick` does) can hand this component a live per-row signal
  * without this component knowing anything about keyboard navigation. A caller with no keyboard nav just passes
  * `_ => Val(false)`.
  *
  * @param widthCls
  *   a Tailwind width class; `CreateTaskQuick`'s name-input popup matches its full-width input (`"w-full""`), its
  *   badge-row popup and a popup anchored to its own small badge both need a fixed width instead
  * @param leftPx
  *   an explicit pixel offset from the nearest positioned ancestor, for a popup that must anchor somewhere other
  *   than its own static position — `CreateTaskQuick`'s shared badge row, where every badge shares one popup slot.
  *   A popup with its own dedicated positioned wrapper (the name input, or one badge in its own wrapper) leaves this
  *   `None` and relies on the browser's static-position fallback for an absolutely positioned element.
  */
case class QuickOptionsPopup(
  options: Seq[QuickOption],
  isHighlighted: Int => Signal[Boolean],
  onChoose: QuickOption => Unit,
  widthCls: String = "w-full",
  leftPx: Option[Double] = None
) {

  val element: HtmlElement =
    div(
      cls(s"absolute z-10 mt-1 $widthCls bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg"),
      leftPx.map(px => left(s"${px}px")),
      ul(
        cls("p-2 text-sm text-body font-medium max-h-72 overflow-y-auto"),
        if (options.isEmpty) Seq(li(cls("p-2 text-sm text-body"), "No matches"))
        else options.zipWithIndex.map { case (option, index) => optionRow(option, index) }
      )
    )

  private def optionRow(option: QuickOption, index: Int): HtmlElement = {
    val highlighted = isHighlighted(index)
    li(
      button(
        `type`("button"),
        cls(
          "flex flex-col items-start w-full p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium rounded-md cursor-pointer"
        ),
        cls("bg-neutral-tertiary-medium") <-- highlighted,
        inContext(row =>
          highlighted.changes.filter(identity) --> { _ =>
            row.ref.asInstanceOf[js.Dynamic].scrollIntoView(js.Dynamic.literal(block = "nearest")): Unit
          }
        ),
        onMouseDown.preventDefault --> Observer.empty,
        onClick.mapToUnit --> (_ => onChoose(option)),
        div(
          cls("inline-flex items-center w-full gap-1.5"),
          div(cls("w-4 h-4 shrink-0"), Option.when(option.isSelected)(TgIcons.checkIcon("w-4 h-4"))),
          option.badgeColor.fold[HtmlElement](span(option.label))(color =>
            TgBadge(text = option.label, color = color, bordered = true).element
          )
        ),
        Option.when(option.description.nonEmpty)(
          p(cls("w-full text-left text-xs font-normal text-body mt-0.5 line-clamp-1"), option.description)
        )
      )
    )
  }
}
