package com.togger.ui.components.wysiwyg

import com.togger.ui.components.wysiwyg.WYSIWYGDom._
import org.scalajs.dom

/** Structural keys, intercepted so contenteditable cannot drift away from the markdown model.
  *
  * Left alone, browsers answer Enter with a `<div>` wrapper or a split that has no markdown equivalent, and answer
  * Backspace at a block edge by nesting one block inside another. Both serialize to something the author did not
  * write.
  *
  * @return
  *   true when the key was handled, meaning the caller prevents the browser's own default
  */
class WYSIWYGKeys(commands: WYSIWYGCommands) {

  def onKeyDown(surface: dom.Element, event: dom.KeyboardEvent): Boolean =
    event.key match {
      case "Enter" if !event.shiftKey => onEnter(surface)
      case "Backspace"                => onBackspace(surface)
      case "Tab"                      => onTab(surface, event.shiftKey)
      case _                          => false
    }

  private def onEnter(surface: dom.Element): Boolean =
    rangeWithin(surface).flatMap(range => blockOf(surface, range.startContainer).map((_, range))).exists {
      case (block, range) =>
        tagOf(block) match {
          case "pre"                      => insertNewline(range)
          case "li" if isEmptyBlock(block) => commands.toggleList(surface, ordered = false)
          case "li"                       => splitInto(block, range, "li")
          case "h1" | "h2" | "h3" | "h4" | "h5" | "h6" => splitInto(block, range, "p")
          case "blockquote" if isEmptyBlock(block)     => commands.setBlock(surface, "p")
          case _                                       => splitInto(block, range, "p")
        }
    }

  private def insertNewline(range: dom.Range): Boolean = {
    val newline = dom.document.createTextNode("\n")

    range.deleteContents()
    range.insertNode(newline)
    caretAt(newline, 1)
    true
  }

  /** Everything from the caret to the end of the block moves into a new sibling. */
  private def splitInto(block: dom.Element, range: dom.Range, tag: String): Boolean = {
    val tail = dom.document.createRange()

    tail.setStart(range.startContainer, range.startOffset)
    if (block.lastChild != null) tail.setEndAfter(block.lastChild) else tail.setEnd(block, 0)

    val moved = tail.extractContents()
    val created = if (tag == "li") commands.element("li", commands.blockClass("li")) else commands.block(tag)

    created.appendChild(moved)
    if (!created.hasChildNodes()) created.appendChild(filler())
    if (!block.hasChildNodes()) block.appendChild(filler())
    Option(block.parentNode).foreach(_.insertBefore(created, block.nextSibling))
    caretAtStartOf(created)
    true
  }

  /** Backspace at the very start of a block merges it into the one above instead of letting the browser nest it. */
  private def onBackspace(surface: dom.Element): Boolean =
    rangeWithin(surface)
      .filter(_.collapsed)
      .flatMap(range => blockOf(surface, range.startContainer).map((_, range)))
      .filter { case (block, range) => atStartOfBlock(block, range) }
      .exists { case (block, _) =>
        Option(block.previousSibling).collect { case e: dom.Element => e } match {
          case Some(previous) if tagOf(previous) == "ul" || tagOf(previous) == "ol" =>
            Option(previous.lastChild).collect { case item: dom.Element => item }.exists(merge(block, _))
          case Some(previous) => merge(block, previous)
          case None           => tagOf(block) == "li" && commands.toggleList(surface, ordered = false)
        }
      }

  private def merge(block: dom.Element, into: dom.Element): Boolean = {
    val boundary = into.lastChild

    childNodes(block).foreach(child => into.appendChild(child))
    Option(block.parentNode).foreach(_.removeChild(block))
    Option(boundary) match {
      case Some(node) => caretAtEndOf(node)
      case None       => caretAtStartOf(into)
    }
    true
  }

  /** Tab only means indent inside a list — anywhere else it has to keep moving focus, or the form becomes a trap
    * for keyboard users.
    */
  private def onTab(surface: dom.Element, outdent: Boolean): Boolean =
    rangeWithin(surface)
      .flatMap(range => blockOf(surface, range.startContainer))
      .filter(block => tagOf(block) == "li")
      .exists(item => if (outdent) outdentItem(surface, item) else indentItem(item))

  private def indentItem(item: dom.Element): Boolean =
    Option(item.previousSibling).collect { case e: dom.Element if tagOf(e) == "li" => e }.exists { previous =>
      val nested = childNodes(previous)
        .collectFirst { case e: dom.Element if tagOf(e) == "ul" || tagOf(e) == "ol" => e }
        .getOrElse {
          val created = commands.block(tagOf(item.parentNode))

          previous.appendChild(created)
          created
        }

      nested.appendChild(item)
      caretAtEndOf(item)
      true
    }

  private def outdentItem(surface: dom.Element, item: dom.Element): Boolean = {
    val list = item.parentNode
    val parentItem = Option(list.parentNode).collect { case e: dom.Element if tagOf(e) == "li" => e }

    parentItem match {
      case Some(outer) =>
        Option(outer.parentNode).foreach(_.insertBefore(item, outer.nextSibling))
        if (!list.hasChildNodes()) outer.removeChild(list)
        caretAtEndOf(item)
        true
      case None => commands.toggleList(surface, ordered = false)
    }
  }
}
