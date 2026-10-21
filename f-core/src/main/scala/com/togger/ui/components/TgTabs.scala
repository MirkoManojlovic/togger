package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.ui.auth.TgHtmlTypes.{HtmlButton, HtmlDiv, HtmlLi, HtmlUl}
import org.scalajs.dom.{KeyboardEvent, SVGSVGElement}

import scala.annotation.tailrec

/** A single tab and the content shown while it is selected.
  *
  * @param id
  *   value stored in the selection `Var`; it also ends up inside a DOM id, so it must be usable there (no spaces)
  * @param content
  *   already built element - it is mounted once and hidden while another tab is selected
  * @param icon
  *   optional leading icon, as a `className => svg` function (e.g. `Some(TgIcons.briefcaseIcon)`)
  * @param badge
  *   optional trailing count, rendered only while the value is greater than zero
  */
case class TgTab(
  id: String,
  label: String,
  content: HtmlElement,
  icon: Option[String => ReactiveSvgElement[SVGSVGElement]] = None,
  badge: Option[Signal[Int]] = None,
  disabled: Boolean = false
)

/** Flowbite "tabs with underline" for Laminar.
  *
  * All panels are mounted up front and the inactive ones are hidden, so panel state survives tab switches and each
  * panel mounts exactly once. Selection is owned by the caller through `selectedVar`.
  */
class TgTabs(
  tabs: Seq[TgTab],
  selectedVar: Var[String],
  idPrefix: String = TgTabs.nextIdPrefix()
) {

  private val barCls = "text-sm font-medium text-center text-body border-b border-default"
  private val listCls = "flex flex-wrap -mb-px"

  private val tabBaseCls =
    "inline-flex items-center p-4 border-b rounded-t-base focus:outline-none " +
      "focus-visible:ring-2 focus-visible:ring-brand-subtle"

  private val tabInactiveCls = "border-transparent hover:text-fg-brand hover:border-brand cursor-pointer"
  private val tabActiveCls = "text-fg-brand border-brand active"
  private val tabDisabledCls = "border-transparent text-fg-disabled cursor-not-allowed"

  private val badgeCls = "ms-2 px-1.5 py-0.5 text-xs font-semibold rounded-full bg-neutral-secondary-medium"
  private val iconCls = "w-4 h-4 me-2"

  val element: HtmlDiv =
    div(
      onMountCallback(_ => selectFallbackWhenUnselectable()),
      tabBar,
      tabPanels
    )

  /** The row of tabs alone, for a page that puts the panels of several rows of tabs in one place. The caller then
    * also keeps the selection selectable: only [[element]] does that.
    */
  lazy val tabBar: HtmlDiv = div(cls(barCls), tabList)

  /** The panels alone, see [[tabBar]]. */
  def panels: Seq[HtmlDiv] = tabPanels

  private lazy val tabButtons: Seq[HtmlButton] = tabs.map(tabButton)

  private lazy val tabPanels: Seq[HtmlDiv] = tabs.map(tabPanel)

  private lazy val tabList: HtmlUl =
    ul(
      role("tablist"),
      cls(listCls),
      onKeyDown --> (event => handleKeyDown(event)),
      tabButtons.zipWithIndex.map { case (buttonElement, index) => tabListItem(buttonElement, index) }
    )

  private def tabListItem(buttonElement: HtmlButton, index: Int): HtmlLi =
    li(cls.toggle("me-2") := index < tabs.size - 1, buttonElement)

  private def tabButton(tab: TgTab): HtmlButton =
    button(
      `type`("button"),
      idAttr(tabId(tab)),
      role("tab"),
      disabled(tab.disabled),
      aria.controls(panelId(tab)),
      aria.selected <-- isSelected(tab),
      tabIndex <-- rovingTabIndex(tab),
      cls(tabBaseCls),
      cls <-- tabCls(tab),
      onClick.mapToUnit --> (_ => select(tab.id)),
      tab.icon.map(mkIcon => mkIcon(iconCls)),
      span(tab.label),
      tab.badge.map(badgeSignal => child <-- badgeSignal.map(renderBadge))
    )

  private def renderBadge(count: Int) =
    if (count > 0) span(cls(badgeCls), count.toString) else emptyNode

  private def tabPanel(tab: TgTab): HtmlDiv = {
    val isHidden = isSelected(tab).map(selected => !selected)
    div(
      idAttr(panelId(tab)),
      role("tabpanel"),
      aria.labelledBy(tabId(tab)),
      tabIndex(0),
      hidden <-- isHidden,
      cls.toggle("hidden") <-- isHidden,
      tab.content.amend(cls("mt-4"))
    )
  }

  private def tabCls(tab: TgTab): Signal[String] =
    if (tab.disabled) Val(tabDisabledCls)
    else selectedVar.signal.map(selected => if (selected == tab.id) tabActiveCls else tabInactiveCls)

  private def rovingTabIndex(tab: TgTab): Signal[Int] =
    isSelected(tab).map(selected => if (selected) 0 else -1)

  private def isSelected(tab: TgTab): Signal[Boolean] =
    selectedVar.signal.map(_ == tab.id)

  private def select(id: String): Unit =
    selectedVar.set(id)

  /** The `Var` belongs to the caller, so it can hold an unknown or disabled id (stale storage, bad route param). */
  private def selectFallbackWhenUnselectable(): Unit = {
    val current = selectedVar.now()
    val isSelectable = tabs.exists(tab => tab.id == current && !tab.disabled)
    if (!isSelectable) tabs.find(tab => !tab.disabled).foreach(tab => select(tab.id))
  }

  private def handleKeyDown(event: KeyboardEvent): Unit = {
    val current = tabs.indexWhere(_.id == selectedVar.now())
    val enabled = tabs.zipWithIndex.collect { case (tab, index) if !tab.disabled => index }.toSet

    val target = event.key match {
      case "ArrowRight" | "ArrowDown" => TgTabsNavigation.next(current, 1, enabled, tabs.size)
      case "ArrowLeft" | "ArrowUp"    => TgTabsNavigation.next(current, -1, enabled, tabs.size)
      case "Home"                     => TgTabsNavigation.first(enabled)
      case "End"                      => TgTabsNavigation.last(enabled)
      case _                          => None
    }

    target.foreach { index =>
      event.preventDefault()
      select(tabs(index).id)
      tabButtons(index).ref.focus()
    }
  }

  private def tabId(tab: TgTab): String = s"$idPrefix-tab-${tab.id}"

  private def panelId(tab: TgTab): String = s"$idPrefix-panel-${tab.id}"

}

object TgTabs {

  private var instanceCounter = 0

  def nextIdPrefix(): String = {
    instanceCounter += 1
    s"tg-tabs-$instanceCounter"
  }

}

object TgTabsNavigation {

  /** Index of the next enabled tab, walking by `step` and wrapping, or `None` when there is nowhere else to go. */
  def next(current: Int, step: Int, enabled: Set[Int], size: Int): Option[Int] = {
    if (size <= 0) return None

    @tailrec
    def walk(candidate: Int, remaining: Int): Option[Int] =
      if (remaining == 0) None
      else if (enabled.contains(candidate) && candidate != current) Some(candidate)
      else walk(Math.floorMod(candidate + step, size), remaining - 1)

    walk(firstCandidate(current, step, size), size)
  }

  /** Nothing selected yet - start at the end the walk is heading away from, instead of skipping it. */
  private def firstCandidate(current: Int, step: Int, size: Int): Int =
    if (current >= 0 && current < size) Math.floorMod(current + step, size)
    else if (step > 0) 0
    else size - 1

  def first(enabled: Set[Int]): Option[Int] =
    if (enabled.isEmpty) None else Some(enabled.min)

  def last(enabled: Set[Int]): Option[Int] =
    if (enabled.isEmpty) None else Some(enabled.max)

}
