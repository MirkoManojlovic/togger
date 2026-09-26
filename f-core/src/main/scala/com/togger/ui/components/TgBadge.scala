package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import com.togger.ui.auth.TgHtmlTypes.{HtmlSpan, TgHtmlElement}
import com.togger.ui.organization.TgIcons
import enumeratum.{Enum, EnumEntry}
import org.scalajs.dom.SVGSVGElement

/** What an interactive badge does.
  *
  * `Link` and `Dismiss` are mutually exclusive - a `<button>` inside an `<a>` is invalid markup and unreachable by
  * keyboard - so they are one ADT rather than two optional fields that could both be set.
  */
sealed trait TgBadgeAction

object TgBadgeAction {
  case class Link(href: String) extends TgBadgeAction
  case class Dismiss(onDismiss: Observer[Unit]) extends TgBadgeAction
}

/** The six badge colors Flowbite's theme exposes with a matching soft background, subtle border and foreground token.
  *
  * Dark mode comes from the theme (`@import "flowbite/src/themes/default"` in `style.css`), so no `dark:` class is
  * written here.
  */
sealed abstract class TgBadgeColor(
  val bgCls: String,
  val textCls: String,
  val borderCls: String,
  val linkHoverCls: String,
  val dismissHoverCls: String
) extends EnumEntry

object TgBadgeColor extends Enum[TgBadgeColor] {

  override def values: IndexedSeq[TgBadgeColor] = findValues

  case object Brand
      extends TgBadgeColor(
        bgCls = "bg-brand-softer",
        textCls = "text-fg-brand-strong",
        borderCls = "border-brand-subtle",
        linkHoverCls = "hover:bg-brand-soft",
        dismissHoverCls = "hover:bg-brand-soft"
      )

  case object Alternative
      extends TgBadgeColor(
        bgCls = "bg-neutral-primary-soft",
        textCls = "text-heading",
        borderCls = "border-default",
        linkHoverCls = "hover:bg-neutral-secondary-medium",
        dismissHoverCls = "hover:bg-neutral-tertiary"
      )

  case object Gray
      extends TgBadgeColor(
        bgCls = "bg-neutral-secondary-medium",
        textCls = "text-heading",
        borderCls = "border-default-medium",
        linkHoverCls = "hover:bg-neutral-tertiary-medium",
        dismissHoverCls = "hover:bg-neutral-quaternary"
      )

  case object Danger
      extends TgBadgeColor(
        bgCls = "bg-danger-soft",
        textCls = "text-fg-danger-strong",
        borderCls = "border-danger-subtle",
        linkHoverCls = "hover:bg-danger-medium",
        dismissHoverCls = "hover:bg-danger-medium"
      )

  case object Success
      extends TgBadgeColor(
        bgCls = "bg-success-soft",
        textCls = "text-fg-success-strong",
        borderCls = "border-success-subtle",
        linkHoverCls = "hover:bg-success-medium",
        dismissHoverCls = "hover:bg-success-medium"
      )

  /** `text-fg-warning`, not `-strong` - the theme has no `fg-warning-strong` token. */
  case object Warning
      extends TgBadgeColor(
        bgCls = "bg-warning-soft",
        textCls = "text-fg-warning",
        borderCls = "border-warning-subtle",
        linkHoverCls = "hover:bg-warning-medium",
        dismissHoverCls = "hover:bg-warning-medium"
      )

}

/** @param dismissPaddingCls
  *   asymmetric, so the dismiss button's own `p-0.5` does not double up on the trailing edge
  * @param squareCls
  *   the fixed box of an icon-only badge
  */
sealed abstract class TgBadgeSize(
  val textCls: String,
  val paddingCls: String,
  val dismissPaddingCls: String,
  val squareCls: String,
  val iconCls: String
) extends EnumEntry

object TgBadgeSize extends Enum[TgBadgeSize] {

  override def values: IndexedSeq[TgBadgeSize] = findValues

  case object Default
      extends TgBadgeSize(
        textCls = "text-xs",
        paddingCls = "px-1.5 py-0.5",
        dismissPaddingCls = "ps-1.5 pe-0.5 py-0.5",
        squareCls = "h-5 w-5",
        iconCls = "w-3 h-3"
      )

  /** `leading-none` on both sizes, unlike Flowbite, which sets it only on the large bordered badge and so renders the
    * two large variants a couple of pixels apart.
    */
  case object Large
      extends TgBadgeSize(
        textCls = "text-sm leading-none",
        paddingCls = "px-2 py-1",
        dismissPaddingCls = "ps-2 pe-1 py-1",
        squareCls = "h-6 w-6",
        iconCls = "w-3.5 h-3.5"
      )

}

/** Flowbite ["badge"](https://flowbite.com/docs/components/badge/) for Laminar - the whole family in one type: two
  * sizes, optional border, pill shape, leading icon, badge as link and dismissable badge.
  *
  * One instance owns one DOM element, so build a new `TgBadge` per badge rendered rather than reusing one.
  *
  * @param icon
  *   optional leading icon, as a `className => svg` function (e.g. `Some(TgIcons.statusIcon)`)
  * @param extraCls
  *   caller-owned outer spacing (`"me-2"`, `"ml-auto"`); the badge ships no margin of its own
  */
case class TgBadge(
  text: String,
  color: TgBadgeColor = TgBadgeColor.Brand,
  size: TgBadgeSize = TgBadgeSize.Default,
  bordered: Boolean = false,
  pill: Boolean = false,
  icon: Option[String => ReactiveSvgElement[SVGSVGElement]] = None,
  action: Option[TgBadgeAction] = None,
  extraCls: String = ""
) {

  private val dismissedVar = Var(false)

  private val containerCls = TgBadgeStyles.container(color, size, bordered, pill, action, extraCls)

  private def iconNode = icon.map(mkIcon => mkIcon(TgBadgeStyles.icon(size)))

  val element: TgHtmlElement = action match {
    case Some(TgBadgeAction.Link(url)) =>
      a(href(url), cls(containerCls), iconNode, text)

    case Some(TgBadgeAction.Dismiss(observer)) =>
      span(
        cls(containerCls),
        cls.toggle(TgBadgeStyles.dismissedCls) <-- dismissedVar.signal,
        iconNode,
        span(text),
        dismissButton(observer)
      )

    case None =>
      span(cls(containerCls), iconNode, text)
  }

  /** Flowbite dismisses through its own JS (`data-dismiss-target`), which this app does not load. Hiding locally covers
    * a caller that renders a fixed set of badges; the observer is what a caller rendering from a signal needs, so it
    * can drop the entry from its own model.
    */
  private def dismissButton(observer: Observer[Unit]) =
    button(
      `type`("button"),
      aria.label("Remove"),
      cls(TgBadgeStyles.dismissButton(color)),
      onClick.mapToUnit --> { _ =>
        dismissedVar.set(true)
        observer.onNext(())
      },
      TgIcons.closeIcon(size.iconCls),
      span(cls("sr-only"), "Remove badge")
    )

}

/** An icon-only badge: a fixed square with no text node, so `label` is its accessible name.
  *
  * Separate from [[TgBadge]] because the shape genuinely differs - always `rounded-full`, no text, no action - and a
  * flag on `TgBadge` would leave `text`, `pill` and `action` meaningless.
  */
case class TgIconBadge(
  icon: String => ReactiveSvgElement[SVGSVGElement],
  label: String,
  color: TgBadgeColor = TgBadgeColor.Brand,
  size: TgBadgeSize = TgBadgeSize.Default,
  bordered: Boolean = true,
  extraCls: String = ""
) {

  val element: HtmlSpan =
    span(
      role("img"),
      aria.label(label),
      title(label),
      cls(TgBadgeStyles.square(color, size, bordered, extraCls)),
      icon(size.iconCls)
    )

}

/** Pure class-string assembly, kept out of the components so it can be unit-tested without a DOM. */
object TgBadgeStyles {

  private val baseCls = "inline-flex items-center font-medium"

  private val focusCls = "focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-subtle"

  /** `hidden!`, not `hidden`: the badge already carries `inline-flex`, and Tailwind emits `.hidden` *before* the
    * `display` utilities, so plain `hidden` loses and a dismissed badge would stay visible. The `[hidden]` attribute
    * loses for the same reason - it sits in an earlier layer. Only the important modifier wins.
    */
  val dismissedCls = "hidden!"

  def container(
    color: TgBadgeColor,
    size: TgBadgeSize,
    bordered: Boolean,
    pill: Boolean,
    action: Option[TgBadgeAction],
    extraCls: String
  ): String =
    join(
      baseCls,
      color.bgCls,
      color.textCls,
      size.textCls,
      paddingCls(size, action),
      if (pill) "rounded-full" else "rounded",
      borderCls(color, bordered),
      linkHoverCls(color, action),
      extraCls
    )

  def square(color: TgBadgeColor, size: TgBadgeSize, bordered: Boolean, extraCls: String): String =
    join(
      "inline-flex items-center justify-center font-medium rounded-full",
      color.bgCls,
      color.textCls,
      size.textCls,
      size.squareCls,
      borderCls(color, bordered),
      extraCls
    )

  def dismissButton(color: TgBadgeColor): String =
    join("inline-flex items-center p-0.5 text-sm bg-transparent rounded-xs", color.dismissHoverCls, focusCls)

  def icon(size: TgBadgeSize): String = s"${size.iconCls} me-1"

  private def paddingCls(size: TgBadgeSize, action: Option[TgBadgeAction]): String = action match {
    case Some(_: TgBadgeAction.Dismiss) => s"${size.dismissPaddingCls} gap-1"
    case _                              => size.paddingCls
  }

  private def borderCls(color: TgBadgeColor, bordered: Boolean): String =
    if (bordered) s"border ${color.borderCls}" else ""

  private def linkHoverCls(color: TgBadgeColor, action: Option[TgBadgeAction]): String = action match {
    case Some(_: TgBadgeAction.Link) => s"${color.linkHoverCls} $focusCls"
    case _                           => ""
  }

  /** Blank fragments are dropped rather than left to collapse into double spaces. */
  private def join(parts: String*): String = parts.map(_.trim).filter(_.nonEmpty).mkString(" ")

}
