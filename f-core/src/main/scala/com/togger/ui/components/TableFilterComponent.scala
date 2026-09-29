package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgCheckbox
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import org.scalajs.dom.HTMLButtonElement

case class MultiSelectItem(id: String, name: String, isSelected: Boolean)

/** @param selectedIdsVar
  *   defaults to a fresh, private `Var` (one filter, one selection). Callers that render several instances of the
  *   same logical filter (e.g. one per table section) pass in a shared `Var` so all instances stay in sync.
  */
class TableFilterComponent(
  itemsSignal: Signal[Seq[MultiSelectItem]],
  label: String,
  selectedIdsVar: Var[Set[String]] = Var(Set.empty)
) {

  private val filterButtonCls =
    "shrink-0 inline-flex items-center justify-center text-body bg-neutral-secondary-medium box-border border border-default-medium hover:bg-neutral-tertiary-medium hover:text-heading focus:ring-4 focus:ring-neutral-tertiary shadow-xs font-medium leading-5 rounded-base text-sm px-3 py-2 focus:outline-none"

  private val isOpenVar = Var(false)
  private val seededIdsVar = Var(Set.empty[String])

  val selectedIds: Signal[Set[String]] = selectedIdsVar.signal

  private def seedNewItems(items: Seq[MultiSelectItem]): Unit = {
    val newItems = items.filterNot(item => seededIdsVar.now().contains(item.id))
    if (newItems.nonEmpty) {
      selectedIdsVar.update(_ ++ newItems.filter(_.isSelected).map(_.id))
      seededIdsVar.update(_ ++ newItems.map(_.id))
    }
  }

  private def toggle(id: String, checked: Boolean): Unit =
    selectedIdsVar.update(current => if (checked) current + id else current - id)

  private def actionRow(actionLabel: String, onClickMod: Modifier[ReactiveHtmlElement[HTMLButtonElement]]) =
    li(
      button(
        `type`("button"),
        cls(
          "w-full text-left p-2 text-sm font-medium hover:bg-neutral-tertiary-medium hover:text-heading rounded cursor-pointer"
        ),
        onClickMod,
        actionLabel
      )
    )

  private def optionRow(item: MultiSelectItem) =
    li(
      TgCheckbox
        .checkBox(
          inputSignal = selectedIdsVar.signal.map(_.contains(item.id)),
          inputStateUpdater = Observer[Boolean](toggle(item.id, _)),
          labelValue = item.name
        )
        .htmlElement
    )

  val element: HtmlDiv =
    div(
      cls("relative inline-block text-left"),
      itemsSignal --> Observer[Seq[MultiSelectItem]](seedNewItems),
      button(
        `type`("button"),
        TgIcons.filterIcon(className = "w-4 h-4 ms-1.5 inline mr-2"),
        cls(filterButtonCls),
        onClick.mapToUnit --> (_ => isOpenVar.update(isOpened => !isOpened)),
        span(label),
        child.text <-- selectedIdsVar.signal.map(ids => if (ids.isEmpty) "" else s" (${ids.size})"),
        TgIcons.chevronDownIcon(className = "w-4 h-4 ms-1.5 inline")
      ),
      child <-- isOpenVar.signal.map {
        case true =>
          div(
            cls(
              "absolute z-10 mt-1 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg w-56"
            ),
            ul(
              cls("p-2 text-sm font-medium max-h-120 overflow-y-auto"),
              actionRow(
                "Select all",
                onClick.compose(_.sample(itemsSignal)) --> (items => selectedIdsVar.set(items.map(_.id).toSet))
              ),
              actionRow("Deselect all", onClick.mapToUnit --> (_ => selectedIdsVar.set(Set.empty))),
              li(cls("border-t border-default my-1")),
              children <-- itemsSignal.map(_.map(optionRow))
            )
          )
        case false => emptyNode
      }
    )

}
