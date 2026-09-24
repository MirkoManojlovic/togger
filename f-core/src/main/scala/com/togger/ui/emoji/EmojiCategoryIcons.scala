package com.togger.ui.emoji

import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveSvgElement
import org.scalajs.dom.svg.SVG

/** Nav icons for the picker's category tabs, written in the same shape as `com.togger.ui.organization.TgIcons`. Kept
  * here rather than in `TgIcons` so the emoji package stays self-contained.
  */
object EmojiCategoryIcons {

  def iconFor(categoryId: EmojiCategoryId, className: String): ReactiveSvgElement[SVG] =
    categoryId match {
      case EmojiCategoryId.Frequent => clockIcon(className)
      case EmojiCategoryId.People   => smileyIcon(className)
      case EmojiCategoryId.Nature   => leafIcon(className)
      case EmojiCategoryId.Foods    => cupIcon(className)
      case EmojiCategoryId.Activity => ballIcon(className)
      case EmojiCategoryId.Places   => pinIcon(className)
      case EmojiCategoryId.Objects  => bulbIcon(className)
      case EmojiCategoryId.Symbols  => hashIcon(className)
      case EmojiCategoryId.Flags    => flagIcon(className)
    }

  private def outlineIcon(className: String, paths: String*): ReactiveSvgElement[SVG] =
    svg.svg(
      svg.className(className),
      svg.width("24"),
      svg.height("24"),
      svg.fill("none"),
      svg.viewBox("0 0 24 24"),
      paths.map(pathData =>
        svg.path(
          svg.stroke("currentColor"),
          svg.strokeLineCap("round"),
          svg.strokeLineJoin("round"),
          svg.strokeWidth("2"),
          svg.d(pathData)
        )
      )
    )

  def clockIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M12 7v5l3 2", "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z")

  def smileyIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(
      className,
      "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z",
      "M9 10h.01",
      "M15 10h.01",
      "M8.5 14.5a4.5 4.5 0 0 0 7 0"
    )

  def leafIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M20 4s-8 0-12 4-4 12-4 12 8 0 12-4 4-12 4-12Z", "M4 20 14 10")

  def cupIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M4 7h12v7a6 6 0 0 1-12 0V7Z", "M16 9h2a3 3 0 0 1 0 6h-1", "M3 21h14")

  def ballIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z", "M12 3v18", "M3 12h18", "M5 6c4 4 10 4 14 0")

  def pinIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(
      className,
      "M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11Z",
      "M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z"
    )

  def bulbIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(
      className,
      "M9 21h6",
      "M10 18h4",
      "M12 3a6 6 0 0 0-3.6 10.8c.6.5.9 1.2.9 1.9V18h5.4v-2.3c0-.7.3-1.4.9-1.9A6 6 0 0 0 12 3Z"
    )

  def hashIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M5 9h14", "M5 15h14", "M10 3 8 21", "M16 3l-2 18")

  def flagIcon(className: String): ReactiveSvgElement[SVG] =
    outlineIcon(className, "M5 21V4", "m5 4 8 3 6-2v10l-6 2-8-3")

}
