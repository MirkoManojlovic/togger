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

/** A badge color, as the five class fragments a badge needs.
  *
  * Two families live here. The **semantic six** (`Brand` .. `Warning`) use Flowbite's theme tokens, which swap
  * themselves under `.dark`, so they carry no `dark:` class. The **palette colors** (`Blue` .. `Rose`) are raw
  * Tailwind shades, which do not, so each spells out its own `dark:` pair. `style.css` points the `dark:` variant at
  * the same `.dark` class the theme uses, so both families flip together.
  *
  * Every class is a literal string on purpose: Tailwind scans source text for whole class names, so a fragment built
  * with interpolation (`s"bg-$hue-100"`) would generate no CSS at all.
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

  case object Blue
      extends TgBadgeColor(
        bgCls = "bg-blue-100 dark:bg-blue-900",
        textCls = "text-blue-800 dark:text-blue-300",
        borderCls = "border-blue-400",
        linkHoverCls = "hover:bg-blue-200 dark:hover:bg-blue-800",
        dismissHoverCls = "hover:bg-blue-200 dark:hover:bg-blue-800"
      )

  case object Red
      extends TgBadgeColor(
        bgCls = "bg-red-100 dark:bg-red-900",
        textCls = "text-red-800 dark:text-red-300",
        borderCls = "border-red-400",
        linkHoverCls = "hover:bg-red-200 dark:hover:bg-red-800",
        dismissHoverCls = "hover:bg-red-200 dark:hover:bg-red-800"
      )

  case object Green
      extends TgBadgeColor(
        bgCls = "bg-green-100 dark:bg-green-900",
        textCls = "text-green-800 dark:text-green-300",
        borderCls = "border-green-400",
        linkHoverCls = "hover:bg-green-200 dark:hover:bg-green-800",
        dismissHoverCls = "hover:bg-green-200 dark:hover:bg-green-800"
      )

  case object Yellow
      extends TgBadgeColor(
        bgCls = "bg-yellow-100 dark:bg-yellow-900",
        textCls = "text-yellow-800 dark:text-yellow-300",
        borderCls = "border-yellow-400",
        linkHoverCls = "hover:bg-yellow-200 dark:hover:bg-yellow-800",
        dismissHoverCls = "hover:bg-yellow-200 dark:hover:bg-yellow-800"
      )

  case object Indigo
      extends TgBadgeColor(
        bgCls = "bg-indigo-100 dark:bg-indigo-900",
        textCls = "text-indigo-800 dark:text-indigo-300",
        borderCls = "border-indigo-400",
        linkHoverCls = "hover:bg-indigo-200 dark:hover:bg-indigo-800",
        dismissHoverCls = "hover:bg-indigo-200 dark:hover:bg-indigo-800"
      )

  case object Purple
      extends TgBadgeColor(
        bgCls = "bg-purple-100 dark:bg-purple-900",
        textCls = "text-purple-800 dark:text-purple-300",
        borderCls = "border-purple-400",
        linkHoverCls = "hover:bg-purple-200 dark:hover:bg-purple-800",
        dismissHoverCls = "hover:bg-purple-200 dark:hover:bg-purple-800"
      )

  case object Pink
      extends TgBadgeColor(
        bgCls = "bg-pink-100 dark:bg-pink-900",
        textCls = "text-pink-800 dark:text-pink-300",
        borderCls = "border-pink-400",
        linkHoverCls = "hover:bg-pink-200 dark:hover:bg-pink-800",
        dismissHoverCls = "hover:bg-pink-200 dark:hover:bg-pink-800"
      )

  /** Flowbite's name for its gray *palette* badge. Distinct from [[Gray]], which is the theme's neutral token and
    * therefore tracks whatever the theme decides neutral means.
    */
  case object Dark
      extends TgBadgeColor(
        bgCls = "bg-gray-100 dark:bg-gray-700",
        textCls = "text-gray-800 dark:text-gray-300",
        borderCls = "border-gray-500",
        linkHoverCls = "hover:bg-gray-200 dark:hover:bg-gray-600",
        dismissHoverCls = "hover:bg-gray-200 dark:hover:bg-gray-600"
      )

  case object Teal
      extends TgBadgeColor(
        bgCls = "bg-teal-100 dark:bg-teal-900",
        textCls = "text-teal-800 dark:text-teal-300",
        borderCls = "border-teal-400",
        linkHoverCls = "hover:bg-teal-200 dark:hover:bg-teal-800",
        dismissHoverCls = "hover:bg-teal-200 dark:hover:bg-teal-800"
      )

  case object Cyan
      extends TgBadgeColor(
        bgCls = "bg-cyan-100 dark:bg-cyan-900",
        textCls = "text-cyan-800 dark:text-cyan-300",
        borderCls = "border-cyan-400",
        linkHoverCls = "hover:bg-cyan-200 dark:hover:bg-cyan-800",
        dismissHoverCls = "hover:bg-cyan-200 dark:hover:bg-cyan-800"
      )

  case object Sky
      extends TgBadgeColor(
        bgCls = "bg-sky-100 dark:bg-sky-900",
        textCls = "text-sky-800 dark:text-sky-300",
        borderCls = "border-sky-400",
        linkHoverCls = "hover:bg-sky-200 dark:hover:bg-sky-800",
        dismissHoverCls = "hover:bg-sky-200 dark:hover:bg-sky-800"
      )

  case object Lime
      extends TgBadgeColor(
        bgCls = "bg-lime-100 dark:bg-lime-900",
        textCls = "text-lime-800 dark:text-lime-300",
        borderCls = "border-lime-400",
        linkHoverCls = "hover:bg-lime-200 dark:hover:bg-lime-800",
        dismissHoverCls = "hover:bg-lime-200 dark:hover:bg-lime-800"
      )

  case object Orange
      extends TgBadgeColor(
        bgCls = "bg-orange-100 dark:bg-orange-900",
        textCls = "text-orange-800 dark:text-orange-300",
        borderCls = "border-orange-400",
        linkHoverCls = "hover:bg-orange-200 dark:hover:bg-orange-800",
        dismissHoverCls = "hover:bg-orange-200 dark:hover:bg-orange-800"
      )

  case object Rose
      extends TgBadgeColor(
        bgCls = "bg-rose-100 dark:bg-rose-900",
        textCls = "text-rose-800 dark:text-rose-300",
        borderCls = "border-rose-400",
        linkHoverCls = "hover:bg-rose-200 dark:hover:bg-rose-800",
        dismissHoverCls = "hover:bg-rose-200 dark:hover:bg-rose-800"
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

  /** A solid ring in the given color, for marking something elsewhere in the UI with a badge's color without
    * drawing a full badge (e.g. a calendar day that already has an entry of that status). Each branch is a literal
    * string for the same Tailwind-scanning reason [[TgBadgeColor]]'s own fields are - see its doc comment.
    */
  def ring(color: TgBadgeColor): String = color match {
    case TgBadgeColor.Brand       => "ring-brand-subtle"
    case TgBadgeColor.Alternative => "ring-default"
    case TgBadgeColor.Gray        => "ring-default-medium"
    case TgBadgeColor.Danger      => "ring-danger-subtle"
    case TgBadgeColor.Success     => "ring-success-subtle"
    case TgBadgeColor.Warning     => "ring-warning-subtle"
    case TgBadgeColor.Blue        => "ring-blue-400"
    case TgBadgeColor.Red         => "ring-red-400"
    case TgBadgeColor.Green       => "ring-green-400"
    case TgBadgeColor.Yellow      => "ring-yellow-400"
    case TgBadgeColor.Indigo      => "ring-indigo-400"
    case TgBadgeColor.Purple      => "ring-purple-400"
    case TgBadgeColor.Pink        => "ring-pink-400"
    case TgBadgeColor.Dark        => "ring-gray-500"
    case TgBadgeColor.Teal        => "ring-teal-400"
    case TgBadgeColor.Cyan        => "ring-cyan-400"
    case TgBadgeColor.Sky         => "ring-sky-400"
    case TgBadgeColor.Lime        => "ring-lime-400"
    case TgBadgeColor.Orange      => "ring-orange-400"
    case TgBadgeColor.Rose        => "ring-rose-400"
  }

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
