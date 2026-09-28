package com.togger.ui.components.wysiwyg

import org.scalajs.dom

/** Selection and tree helpers shared by the command, input-rule and key handlers.
  *
  * Everything here is scoped to a surface element: a walk never escapes past it, so the editor can never restructure
  * the page around itself.
  */
object WYSIWYGDom {

  val blockTags: Set[String] =
    Set("p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "li", "ul", "ol", "div", "hr", "table")

  val markTags: Set[String] = Set("strong", "em", "del", "code", "a")

  def tagOf(node: dom.Node): String = node match {
    case e: dom.Element => e.tagName.toLowerCase
    case _              => ""
  }

  def childNodes(node: dom.Node): Seq[dom.Node] = {
    val nodes = node.childNodes

    (0 until nodes.length).map(nodes(_))
  }

  def selection: Option[dom.Selection] = Option(dom.window.getSelection()).filter(_.rangeCount > 0)

  def rangeWithin(surface: dom.Element): Option[dom.Range] =
    selection.map(_.getRangeAt(0)).filter(range => contains(surface, range.startContainer))

  def contains(ancestor: dom.Node, node: dom.Node): Boolean = {
    var current = node

    while (current != null && current != ancestor) current = current.parentNode
    current != null
  }

  /** Walks up from `node` to the nearest element satisfying `matches`, stopping at the surface. */
  def closest(surface: dom.Element, node: dom.Node, matches: dom.Element => Boolean): Option[dom.Element] = {
    var current: dom.Node = node

    while (current != null && current != surface) {
      current match {
        case e: dom.Element if matches(e) => return Some(e)
        case _                            => ()
      }
      current = current.parentNode
    }
    None
  }

  /** The block the caret sits in. A list is skipped in favour of its item, which is the thing edits act on. */
  def blockOf(surface: dom.Element, node: dom.Node): Option[dom.Element] =
    closest(surface, node, e => blockTags.contains(tagOf(e)) && tagOf(e) != "ul" && tagOf(e) != "ol")

  def markOf(surface: dom.Element, node: dom.Node, tag: String): Option[dom.Element] =
    closest(surface, node, e => tagOf(e) == tag)

  /** The top-level block, i.e. the direct child of the surface the caret is under. */
  def topBlockOf(surface: dom.Element, node: dom.Node): Option[dom.Element] = {
    var current: dom.Node = node

    while (current != null && current.parentNode != surface) current = current.parentNode
    Option(current).collect { case e: dom.Element => e }
  }

  def firstTextNode(node: dom.Node): Option[dom.Text] = node match {
    case text: dom.Text => Some(text)
    case _              => childNodes(node).view.flatMap(firstTextNode).headOption
  }

  def lastTextNode(node: dom.Node): Option[dom.Text] = node match {
    case text: dom.Text => Some(text)
    case _              => childNodes(node).reverse.view.flatMap(lastTextNode).headOption
  }

  def caretAt(node: dom.Node, offset: Int): Unit = {
    val range = dom.document.createRange()

    range.setStart(node, offset)
    range.collapse(true)
    replaceSelection(range)
  }

  def caretAtStartOf(node: dom.Node): Unit =
    firstTextNode(node) match {
      case Some(text) => caretAt(text, 0)
      case None       => caretAt(node, 0)
    }

  def caretAtEndOf(node: dom.Node): Unit =
    lastTextNode(node) match {
      case Some(text) => caretAt(text, text.data.length)
      case None       => caretAt(node, childNodes(node).length)
    }

  def replaceSelection(range: dom.Range): Unit =
    Option(dom.window.getSelection()).foreach { selection =>
      selection.removeAllRanges()
      selection.addRange(range)
    }

  /** True when the caret sits before every character of its block — what Backspace-merges the block into the one
    * above.
    */
  def atStartOfBlock(block: dom.Element, range: dom.Range): Boolean =
    range.collapsed && (firstTextNode(block) match {
      case Some(text) => range.startContainer == text && range.startOffset == 0
      case None       => true
    })

  def isEmptyBlock(block: dom.Element): Boolean = block.textContent.trim.isEmpty

  /** contenteditable collapses a block with no content, so an empty one keeps a `<br>` to stay clickable. The
    * serializer drops it.
    */
  def filler(): dom.Element = dom.document.createElement("br")

  def replaceChildren(target: dom.Node, replacements: Seq[dom.Node]): Unit = {
    while (target.firstChild != null) target.removeChild(target.firstChild)
    replacements.foreach(target.appendChild)
  }

  def unwrap(element: dom.Element): Unit = {
    val parent = element.parentNode

    if (parent != null) {
      while (element.firstChild != null) parent.insertBefore(element.firstChild, element)
      parent.removeChild(element)
    }
  }
}
