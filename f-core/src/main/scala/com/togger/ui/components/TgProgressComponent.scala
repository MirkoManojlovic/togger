package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.HtmlDiv
import enumeratum.{Enum, EnumEntry}

/** Where the percentage is shown. An ADT, not a flag plus an optional title: only `Outside` has a title. */
sealed trait TgProgressLabel

object TgProgressLabel {
  case object None extends TgProgressLabel
  case object Inside extends TgProgressLabel
  case class Outside(title: String) extends TgProgressLabel
}

/** Fill colors, as Flowbite theme tokens, which swap themselves under `.dark` and so carry no `dark:` class.
  *
  * Every class is a literal string on purpose: Tailwind scans source text for whole class names.
  */
sealed abstract class TgProgressColor(val fillCls: String) extends EnumEntry

object TgProgressColor extends Enum[TgProgressColor] {

  override def values: IndexedSeq[TgProgressColor] = findValues

  case object Brand extends TgProgressColor("bg-brand")
  case object Dark extends TgProgressColor("bg-dark")
  case object Success extends TgProgressColor("bg-success")
  case object Danger extends TgProgressColor("bg-danger")
  case object Warning extends TgProgressColor("bg-warning")
}

/** @param barCls
  *   height of a plain bar
  * @param insideCls
  *   height and text size of a bar that holds its label; Flowbite ships only the `Default` one, the others are derived
  */
sealed abstract class TgProgressSize(val barCls: String, val insideCls: String) extends EnumEntry

object TgProgressSize extends Enum[TgProgressSize] {

  override def values: IndexedSeq[TgProgressSize] = findValues

  /** Too thin to hold text, so an inside label falls back to the `Default` inside height. */
  case object Small extends TgProgressSize("h-1.5", "h-4 text-xs")
  case object Default extends TgProgressSize("h-2", "h-4 text-xs")
  case object Large extends TgProgressSize("h-2.5", "h-5 text-xs")
  case object ExtraLarge extends TgProgressSize("h-4", "h-6 text-sm")
}

/** Flowbite ["progress"](https://flowbite.com/docs/components/progress/) for Laminar: plain bar, label inside the fill
  * or title and percent above the bar.
  *
  * One instance owns one DOM element, so build a new `TgProgressComponent` per bar rendered.
  *
  * @param extraCls
  *   caller-owned outer spacing; the bar ships no margin of its own
  */
case class TgProgressComponent(
  value: Int,
  max: Int = 100,
  color: TgProgressColor = TgProgressColor.Brand,
  size: TgProgressSize = TgProgressSize.Default,
  label: TgProgressLabel = TgProgressLabel.None,
  extraCls: String = ""
) {

  private val percent = TgProgressStyles.percent(value, max)

  private val percentText = TgProgressStyles.percentText(percent)

  private val fill: HtmlDiv =
    div(
      cls(TgProgressStyles.fill(color, size, label)),
      role("progressbar"),
      aria.valueNow(percent),
      aria.valueMin(0),
      aria.valueMax(100),
      width(percentText),
      label match {
        case TgProgressLabel.Inside        => percentText
        case TgProgressLabel.Outside(name) => aria.label(name)
        case TgProgressLabel.None          => emptyMod
      }
    )

  private val track: HtmlDiv = div(cls(TgProgressStyles.track(label, extraCls)), fill)

  val element: HtmlDiv = label match {
    case TgProgressLabel.Outside(title) =>
      div(
        cls(extraCls),
        div(
          cls(TgProgressStyles.outsideRowCls),
          span(cls(TgProgressStyles.outsideTextCls), title),
          span(cls(TgProgressStyles.outsideTextCls), percentText)
        ),
        track
      )
    case _ => track
  }

}

/** Pure class-string and percent logic, kept out of the component so it can be unit-tested without a DOM. */
object TgProgressStyles {

  val outsideRowCls = "flex justify-between mb-1"

  val outsideTextCls = "text-sm font-medium text-body"

  private val trackCls = "w-full bg-neutral-quaternary rounded-full"

  private val insideFillCls = "flex items-center justify-center text-center font-medium text-white leading-none p-0.5"

  def percent(value: Int, max: Int): Int =
    if (max <= 0) 0
    else math.round(value.toDouble / max * 100).toInt.max(0).min(100)

  def percentText(percent: Int): String = s"$percent%"

  /** With an outside label the wrapper owns `extraCls`, so the track must not repeat it. */
  def track(label: TgProgressLabel, extraCls: String): String = label match {
    case _: TgProgressLabel.Outside => trackCls
    case _                          => join(trackCls, extraCls)
  }

  def fill(color: TgProgressColor, size: TgProgressSize, label: TgProgressLabel): String = label match {
    case TgProgressLabel.Inside => join("rounded-full", color.fillCls, size.insideCls, insideFillCls)
    case _                      => join("rounded-full", color.fillCls, size.barCls)
  }

  /** Blank fragments are dropped rather than left to collapse into double spaces. */
  private def join(parts: String*): String = parts.map(_.trim).filter(_.nonEmpty).mkString(" ")

}
