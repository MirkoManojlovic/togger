package com.togger.ui.components.wysiwyg

import com.togger.markdown.TgMarkdown
import io.github.edadma.markdown.MarkdownConfig
import org.scalajs.dom

/** A detached editing surface plus the selection helpers the command, rule and key suites all need.
  *
  * The surface is attached to the document body: a `Selection` cannot be placed inside a node that is not in the
  * document, so a detached div would make every test here silently do nothing.
  */
object WYSIWYGTestDom {

  def surface(markdown: String, config: MarkdownConfig = MarkdownConfig.default): dom.Element = {
    val element = dom.document.createElement("div")

    element.setAttribute("contenteditable", "true")
    dom.document.body.appendChild(element)
    new TgMarkdown(config).toLaminar(markdown).foreach(block => element.appendChild(block.ref))
    element
  }

  def discard(element: dom.Element): Unit =
    Option(element.parentNode).foreach(_.removeChild(element))

  def serialize(element: dom.Element): String = MarkdownDomSerializer.serialize(element)

  /** The nth text node in document order, which is how these tests name a place to put the caret. */
  def textNode(root: dom.Node, index: Int = 0): dom.Text = textNodes(root)(index)

  def textNodes(root: dom.Node): Seq[dom.Text] = root match {
    case text: dom.Text => Seq(text)
    case node           => WYSIWYGDom.childNodes(node).flatMap(textNodes)
  }

  def select(node: dom.Text, start: Int, end: Int): Unit = {
    val range = dom.document.createRange()

    range.setStart(node, start)
    range.setEnd(node, end)
    WYSIWYGDom.replaceSelection(range)
  }

  def caret(node: dom.Text, offset: Int): Unit = select(node, offset, offset)

  def selectWord(root: dom.Node, word: String): Unit = {
    val found = textNodes(root).find(_.data.contains(word)).get
    val start = found.data.indexOf(word)

    select(found, start, start + word.length)
  }
}
