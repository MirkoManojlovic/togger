package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgCheckbox
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import org.scalajs.dom
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
          "w-full text-left p-2 text-sm font-medium text-heading hover:bg-neutral-tertiary-medium rounded cursor-pointer"
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

  private val panelWidthPx = 224.0
  private val panelMaxHeightPx = 480.0
  private val viewportMarginPx = 8.0

  private val trigger: HtmlElement =
    button(
      `type`("button"),
      TgIcons.filterIcon(className = "w-4 h-4 ms-1.5 inline mr-2"),
      cls(filterButtonCls),
      onClick.mapToUnit --> (_ => isOpenVar.update(isOpened => !isOpened)),
      span(label),
      child.text <-- selectedIdsVar.signal.map(ids => if (ids.isEmpty) "" else s" (${ids.size})"),
      TgIcons.chevronDownIcon(className = "w-4 h-4 ms-1.5 inline")
    )

  /** The panel is fixed to the viewport next to its button rather than absolute inside it, since the table it sits in
    * scrolls and would otherwise cut the panel off or make the table scroll. It opens below the button, or above it
    * when there is more room there.
    */
  private def panel: HtmlElement = {
    val rect = trigger.ref.getBoundingClientRect()
    val spaceBelow = dom.window.innerHeight - rect.bottom - viewportMarginPx
    val spaceAbove = rect.top - viewportMarginPx
    val opensBelow = spaceBelow >= panelMaxHeightPx || spaceBelow >= spaceAbove
    val leftPx = rect.left.min(dom.window.innerWidth - panelWidthPx - viewportMarginPx).max(viewportMarginPx)
    div(
      cls("fixed z-20 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg w-56"),
      left(s"${leftPx}px"),
      if (opensBelow) top(s"${rect.bottom + 4}px") else bottom(s"${dom.window.innerHeight - rect.top + 4}px"),
      ul(
        cls("p-2 text-sm font-medium overflow-y-auto"),
        maxHeight.px((if (opensBelow) spaceBelow else spaceAbove).min(panelMaxHeightPx)),
        actionRow(
          "Select all",
          onClick.compose(_.sample(itemsSignal)) --> (items => selectedIdsVar.set(items.map(_.id).toSet))
        ),
        actionRow("Deselect all", onClick.mapToUnit --> (_ => selectedIdsVar.set(Set.empty))),
        li(cls("border-t border-default my-1")),
        children <-- itemsSignal.map(_.map(optionRow))
      )
    )
  }

  val element: HtmlDiv =
    div(
      cls("relative inline-block text-left"),
      itemsSignal --> Observer[Seq[MultiSelectItem]](seedNewItems),
      trigger,
      child <-- isOpenVar.signal.map(if (_) panel else emptyNode)
    )

}
