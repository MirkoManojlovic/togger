package com.togger.ui.components

import com.raquo.laminar.api.L._
import com.togger.ui.auth.TgHtmlTypes.{HtmlLi, HtmlUl}

/** Classes follow Flowbite's typography/lists example (https://flowbite.com/docs/typography/lists/), with the
  * nested `<ul>` kept on `list-disc` (bullets) rather than Flowbite's own `list-decimal` nested example, since this
  * parser's nested items are still unordered bullet items, never a numbered list.
  */
class TextToHtmlRender {

  private val listCls = "text-body list-disc list-inside space-y-1"
  private val nestedListCls = "list-disc list-inside ps-5 mt-2 space-y-1"

  def textToHtmlList(input: String): HtmlUl =
    ul(cls(listCls), TextToHtmlListParser.parse(input).map(renderItem))

  private def renderItem(item: TextToHtmlListParser.ListItem): HtmlLi =
    li(
      item.text,
      item.children match {
        case Nil      => emptyMod
        case children => ul(cls(nestedListCls), children.map(renderItem))
      }
    )
}

object TextToHtmlListParser {

  case class ListItem(text: String, children: Seq[ListItem] = Nil)

  private val bulletPattern = """^(\s*)[-*+]\s+(.*)$""".r

  def parse(input: String): Seq[ListItem] = {
    val matched = input.split("\n", -1).toSeq.flatMap {
      case bulletPattern(indent, text) => Some((indent.length, text.trim))
      case _                           => None
    }

    if (matched.isEmpty) {
      val trimmed = input.trim
      if (trimmed.isEmpty) Nil else Seq(ListItem(trimmed))
    } else {
      buildTree(matched)
    }
  }

  /** One level of nesting: anything indented deeper than the current top-level item's indent becomes its child. */
  private def buildTree(lines: Seq[(Int, String)]): Seq[ListItem] = {
    val topLevelIndent = lines.map(_._1).min

    lines.foldLeft(Seq.empty[ListItem]) {
      case (acc, (indent, text)) if indent <= topLevelIndent || acc.isEmpty =>
        acc :+ ListItem(text)
      case (acc, (_, text)) =>
        val last = acc.last
        acc.init :+ last.copy(children = last.children :+ ListItem(text))
    }
  }
}
