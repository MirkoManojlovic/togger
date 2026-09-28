package com.togger.ui.components.wysiwyg

import com.togger.markdown.TgMarkdownStyles
import com.togger.ui.components.wysiwyg.WYSIWYGDom._
import org.scalajs.dom

/** Toolbar and shortcut operations, as DOM surgery on the current selection.
  *
  * The DOM is the model here, so a command builds the element the serializer will read — `toggleInline("strong")`
  * produces a real `<strong>`, which comes back out as `**…**`.
  *
  * `document.execCommand` is deliberately not used: it is deprecated, and what it emits differs per browser, which
  * would land straight in the serializer's lap.
  *
  * A class rather than an object because every element it creates carries the same `TgMarkdownStyles` classes a
  * rendered one would — otherwise a heading made from the toolbar would sit unstyled until the next repaint.
  */
class WYSIWYGCommands(styles: TgMarkdownStyles) {

  def blockClass(tag: String): String = tag match {
    case "h1"         => styles.heading1
    case "h2"         => styles.heading2
    case "h3"         => styles.heading3
    case "h4"         => styles.heading4
    case "h5"         => styles.heading5
    case "h6"         => styles.heading6
    case "p"          => styles.paragraph
    case "blockquote" => styles.blockQuote
    case "ul"         => styles.bulletList
    case "ol"         => styles.orderedList
    case "li"         => styles.listItem
    case "pre"        => styles.codeBlock
    case _            => ""
  }

  def inlineClass(tag: String): String = tag match {
    case "strong" => styles.strong
    case "em"     => styles.emphasis
    case "del"    => styles.strikethrough
    case "code"   => styles.codeSpan
    case "a"      => styles.link
    case _        => ""
  }

  def element(tag: String, className: String): dom.Element = {
    val created = dom.document.createElement(tag)

    if (className.nonEmpty) created.setAttribute("class", className)
    created
  }

  def block(tag: String): dom.Element = element(tag, blockClass(tag))

  private def classOf(e: dom.Element): String = Option(e.getAttribute("class")).getOrElse("")

  /** Wraps the selection in `tag`, or takes it back out when it is already inside one. */
  def toggleInline(surface: dom.Element, tag: String): Boolean =
    rangeWithin(surface).exists { range =>
      markOf(surface, range.startContainer, tag) match {
        case Some(mark) => splitOut(mark, range); true
        case None       => wrapRange(range, tag)
      }
    }

  private def wrapRange(range: dom.Range, tag: String): Boolean =
    if (range.collapsed) false
    else {
      val wrapper = element(tag, inlineClass(tag))
      val contents = range.extractContents()

      wrapper.appendChild(contents)
      range.insertNode(wrapper)
      selectContents(wrapper)
      true
    }

  /** Removes `mark` from the selected span only, keeping whatever fell outside it still marked.
    *
    * The trailing part is extracted before the leading one: both boundaries can sit in the same text node, and
    * taking the front out first would shift the back one.
    */
  private def splitOut(mark: dom.Element, range: dom.Range): Unit = {
    val document = dom.document
    val parent = mark.parentNode
    val tag = tagOf(mark)

    val after =
      if (mark.lastChild == null) None
      else {
        val tail = document.createRange()

        tail.setStart(range.endContainer, range.endOffset)
        tail.setEndAfter(mark.lastChild)
        Some(tail.extractContents()).filter(_.hasChildNodes())
      }

    val before =
      if (mark.firstChild == null) None
      else {
        val head = document.createRange()

        head.setStartBefore(mark.firstChild)
        head.setEnd(range.startContainer, range.startOffset)
        Some(head.extractContents()).filter(_.hasChildNodes())
      }

    val middle = document.createDocumentFragment()

    while (mark.firstChild != null) middle.appendChild(mark.firstChild)

    before.foreach { fragment =>
      val kept = element(tag, classOf(mark))

      kept.appendChild(fragment)
      parent.insertBefore(kept, mark)
    }

    val anchor = document.createTextNode("")

    parent.insertBefore(anchor, mark)
    parent.insertBefore(middle, mark)

    after.foreach { fragment =>
      val kept = element(tag, classOf(mark))

      kept.appendChild(fragment)
      parent.insertBefore(kept, mark)
    }

    parent.removeChild(mark)
    Option(anchor.nextSibling).foreach(node => caretAtEndOf(node))
    parent.removeChild(anchor)
  }

  private def selectContents(node: dom.Node): Unit = {
    val range = dom.document.createRange()

    range.selectNodeContents(node)
    replaceSelection(range)
  }

  /** Retags the containing block, keeping its content. A list item is lifted out of its list first. */
  def setBlock(surface: dom.Element, tag: String): Boolean =
    rangeWithin(surface).flatMap(range => blockOf(surface, range.startContainer)).exists { current =>
      val target = if (tagOf(current) == "li") liftOut(surface, current) else current
      val replacement = block(tag)

      while (target.firstChild != null) replacement.appendChild(target.firstChild)
      if (!replacement.hasChildNodes()) replacement.appendChild(filler())
      Option(target.parentNode).foreach(_.replaceChild(replacement, target))
      caretAtEndOf(replacement)
      true
    }

  /** Moves a list item out of its list and returns it as a paragraph sitting where the list was. */
  private def liftOut(surface: dom.Element, item: dom.Element): dom.Element = {
    val list = item.parentNode
    val paragraph = block("p")

    while (item.firstChild != null) paragraph.appendChild(item.firstChild)
    Option(list.parentNode).foreach(_.insertBefore(paragraph, list.nextSibling))
    list.removeChild(item)
    if (!list.hasChildNodes()) Option(list.parentNode).foreach(_.removeChild(list))
    paragraph
  }

  def toggleList(surface: dom.Element, ordered: Boolean): Boolean =
    rangeWithin(surface).flatMap(range => blockOf(surface, range.startContainer)).exists { current =>
      if (tagOf(current) == "li") {
        val paragraph = liftOut(surface, current)

        caretAtEndOf(paragraph)
      } else {
        val list = block(if (ordered) "ol" else "ul")
        val item = block("li")

        while (current.firstChild != null) item.appendChild(current.firstChild)
        list.appendChild(item)
        Option(current.parentNode).foreach(_.replaceChild(list, current))
        caretAtEndOf(item)
      }
      true
    }

  def insertLink(surface: dom.Element, destination: String): Boolean =
    rangeWithin(surface).exists { range =>
      val anchor = element("a", inlineClass("a"))

      anchor.setAttribute("href", destination)
      if (range.collapsed) {
        anchor.textContent = destination
        range.insertNode(anchor)
      } else {
        anchor.appendChild(range.extractContents())
        range.insertNode(anchor)
      }
      caretAtEndOf(anchor)
      true
    }

  /** What the caret is inside, for the toolbar's pressed state. */
  def activeMarks(surface: dom.Element): Set[String] =
    rangeWithin(surface)
      .map { range =>
        var current: dom.Node = range.startContainer
        var found = Set.empty[String]

        while (current != null && current != surface) {
          val tag = tagOf(current)

          if (markTags.contains(tag) || blockTags.contains(tag)) found += tag
          current = current.parentNode
        }
        found
      }
      .getOrElse(Set.empty)
}
