package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.modifiers.Modifier
import com.raquo.laminar.nodes.ReactiveHtmlElement
import com.togger.ui.auth.TgCheckbox
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import com.togger.ui.organization.TgIcons
import org.scalajs.dom
import org.scalajs.dom.HTMLButtonElement

/** @param group
  *   a heading the item is listed under; consecutive items with the same group share one heading.
  */
case class MultiSelectItem(id: String, name: String, isSelected: Boolean, group: Option[String] = None)

/** @param selectedIdsVar
  *   defaults to a fresh, private `Var` (one filter, one selection). Callers that render several instances of the same
  *   logical filter (e.g. one per table section) pass in a shared `Var` so all instances stay in sync.
  */
class TableFilterComponent(
  itemsSignal: Signal[Seq[MultiSelectItem]],
  label: String,
  selectedIdsVar: Var[Set[String]] = Var(Set.empty)
) {

  private val filterButtonCls =
    "shrink-0 inline-flex items-center justify-center text-body bg-neutral-secondary-medium box-border border border-default-medium hover:bg-neutral-tertiary-medium hover:text-heading focus:ring-4 focus:ring-neutral-tertiary shadow-xs font-medium leading-5 rounded-base text-sm px-2 py-1 focus:outline-none"

  private val isOpenVar = Var(TableFilterComponent.takeReopen(label))
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

  private def groupLinkButton(linkLabel: String, onClickMod: Modifier[ReactiveHtmlElement[HTMLButtonElement]]) =
    button(
      `type`("button"),
      cls("normal-case font-medium text-heading hover:underline cursor-pointer"),
      onClickMod,
      linkLabel
    )

  /** The heading of a group, with links that tick or untick every item of the group. */
  private def groupRow(group: String, groupIds: Set[String]) =
    li(
      cls("flex items-center justify-between px-1 pt-2 pb-1 text-xs font-semibold uppercase text-body"),
      span(cls("truncate"), group),
      span(
        cls("flex shrink-0 gap-2 ps-2"),
        groupLinkButton("All", onClick.mapToUnit --> (_ => selectedIdsVar.update(_ ++ groupIds))),
        groupLinkButton("None", onClick.mapToUnit --> (_ => selectedIdsVar.update(_ -- groupIds)))
      )
    )

  /** The options, each run of items of one group below that group's heading. */
  private def optionRows(items: Seq[MultiSelectItem]): Seq[HtmlElement] =
    items.zipWithIndex.flatMap { case (item, index) =>
      val startsGroup = item.group.exists(group => index == 0 || !items(index - 1).group.contains(group))
      val heading = item.group.filter(_ => startsGroup).map { group =>
        groupRow(group, items.filter(_.group.contains(group)).map(_.id).toSet)
      }
      heading.toSeq :+ optionRow(item)
    }

  private val panelWidthPx = 224.0
  private val panelMaxHeightPx = 480.0
  private val viewportMarginPx = 8.0

  private val trigger: HtmlElement =
    button(
      `type`("button"),
      cls(filterButtonCls),
      onClick.mapToUnit --> (_ => isOpenVar.update(isOpened => !isOpened)),
      // A choice is what rebuilds the page: the rebuilt filter opens again.
      selectedIdsVar.signal.changes --> Observer[Set[String]](_ => TableFilterComponent.reopenAfterReload(label)),
      onClick.mapToUnit --> (_ => TableFilterComponent.forgetReopen()),
      span(label),
      child.text <-- selectedIdsVar.signal.map(ids => if (ids.isEmpty) "" else s" (${ids.size})"),
      TgIcons.chevronDownIcon(className = "w-4 h-4 ms-1.5 inline")
    )

  private case class PanelPosition(leftPx: Double, anchor: Either[Double, Double], maxHeightPx: Double)

  /** Where the panel goes: below the button, or above it when there is more room there. */
  private def measurePosition(): PanelPosition = {
    val rect = trigger.ref.getBoundingClientRect()
    val spaceBelow = dom.window.innerHeight - rect.bottom - viewportMarginPx
    val spaceAbove = rect.top - viewportMarginPx
    val opensBelow = spaceBelow >= panelMaxHeightPx || spaceBelow >= spaceAbove
    PanelPosition(
      leftPx = rect.left.min(dom.window.innerWidth - panelWidthPx - viewportMarginPx).max(viewportMarginPx),
      anchor = if (opensBelow) Left(rect.bottom + 4) else Right(dom.window.innerHeight - rect.top + 4),
      maxHeightPx = (if (opensBelow) spaceBelow else spaceAbove).min(panelMaxHeightPx)
    )
  }

  /** The panel is fixed to the viewport next to its button rather than absolute inside it, since the table it sits in
    * scrolls and would otherwise cut the panel off or make the table scroll. Because it is fixed, it is placed again
    * whenever anything scrolls (the table sideways, the page) or the window is resized, so it stays under its button.
    */
  private def panel: HtmlElement = {
    val positionVar = Var(measurePosition())
    var watching = false
    // The button moves without any scroll or resize event while the page loads and the table is laid out, so the
    // position is checked on every frame while the panel is open.
    def watch(): Unit = {
      val next = measurePosition()
      if (next != positionVar.now()) positionVar.set(next)
      if (watching) dom.window.requestAnimationFrame(_ => watch()): Unit
    }
    div(
      cls("fixed z-20 bg-neutral-primary-medium border border-default-medium rounded-base shadow-lg w-56"),
      left <-- positionVar.signal.map(position => s"${position.leftPx}px"),
      top <-- positionVar.signal.map(_.anchor.fold(px => s"${px}px", _ => "auto")),
      bottom <-- positionVar.signal.map(_.anchor.fold(_ => "auto", px => s"${px}px")),
      onMountUnmountCallback(
        _ => {
          watching = true
          watch()
        },
        _ => watching = false
      ),
      ul(
        cls("p-2 text-sm font-medium overflow-y-auto"),
        maxHeight <-- positionVar.signal.map(position => s"${position.maxHeightPx}px"),
        actionRow(
          "Select all",
          onClick.compose(_.sample(itemsSignal)) --> (items => selectedIdsVar.set(items.map(_.id).toSet))
        ),
        actionRow("Deselect all", onClick.mapToUnit --> (_ => selectedIdsVar.set(Set.empty))),
        li(cls("border-t border-default my-1")),
        children <-- itemsSignal.map(optionRows)
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

/** Choosing an option navigates, which rebuilds the page and with it every filter. The filter that was open is
  * remembered, and the rebuilt one takes the memory and opens again. The memory is also dropped when the reload ends
  * (the spinner stops), so a filter that was not rebuilt does not leave it behind for a later page.
  */
object TableFilterComponent {

  private var reopenLabel: Option[String] = None

  private def reopenAfterReload(label: String): Unit = reopenLabel = Some(label)

  private def takeReopen(label: String): Boolean = {
    val reopen = reopenLabel.contains(label)
    if (reopen) reopenLabel = None
    reopen
  }

  /** Called when a reload has ended, or the user closed or opened a filter by hand. */
  def forgetReopen(): Unit = reopenLabel = None
}
