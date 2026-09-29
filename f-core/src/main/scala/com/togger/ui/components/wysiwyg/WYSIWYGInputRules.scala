package com.togger.ui.components.wysiwyg

import com.togger.ui.components.wysiwyg.WYSIWYGDom._
import org.scalajs.dom

import scala.util.matching.Regex

/** Markdown typed as text becomes the real thing, which is what separates this from a styled textarea.
  *
  * Inline rules fire on the closing marker rather than on a following space, so `**bold**` is bold the moment it is
  * closed. Block rules fire on the space after the marker, because `#` alone is a legitimate character.
  *
  * Every rule rewrites the DOM, which is why the editor owns undo (see [[WYSIWYGHistory]]) rather than leaving it
  * to the browser.
  */
class WYSIWYGInputRules(commands: WYSIWYGCommands) {

  /** Longest marker first: `**a**` must not be read as `*` + `*a*` + `*`. */
  private val inlineRules: Seq[(Regex, String)] =
    Seq(
      """\*\*([^*\s](?:[^*]*[^*\s])?)\*\*$""".r -> "strong",
      """~~([^~\s](?:[^~]*[^~\s])?)~~$""".r -> "del",
      """`([^`]+)`$""".r -> "code",
      """\*([^*\s](?:[^*]*[^*\s])?)\*$""".r -> "em"
    )

  private val headingRule = """^(#{1,6}) $""".r
  private val bulletRule = """^[-*+] $""".r
  private val orderedRule = """^\d+[.)] $""".r
  private val quoteRule = """^> $""".r

  /** @return true when something was rewritten, so the caller knows to re-read the document */
  def apply(surface: dom.Element): Boolean =
    rangeWithin(surface).filter(_.collapsed).exists { range =>
      range.startContainer match {
        case text: dom.Text => blockRule(surface, text, range) || inlineRule(text, range)
        case _              => false
      }
    }

  /** A browser inserts U+00A0 for a space that would otherwise collapse — which is every space typed at the end of
    * the content, and so every block marker the author types. Matching treats it as an ordinary space; the
    * replacement keeps the same length, so offsets taken from `typed` still line up with the real text node.
    */
  private def withPlainSpaces(text: String): String = text.replace('\u00a0', ' ')

  private def blockRule(surface: dom.Element, text: dom.Text, range: dom.Range): Boolean = {
    val typed = withPlainSpaces(text.data.substring(0, range.startOffset))
    val isBlockStart = blockOf(surface, text).exists(block => firstTextNode(block).contains(text))

    if (!isBlockStart) false
    else
      typed match {
        case headingRule(hashes) => applyBlockRule(text, typed, commands.setBlock(surface, s"h${hashes.length}"))
        case bulletRule()        => applyBlockRule(text, typed, commands.toggleList(surface, ordered = false))
        case orderedRule()       => applyBlockRule(text, typed, commands.toggleList(surface, ordered = true))
        case quoteRule()         => applyBlockRule(text, typed, commands.setBlock(surface, "blockquote"))
        case _                   => false
      }
  }

  /** The marker is dropped before the block is converted — afterwards the text node may have moved. */
  private def applyBlockRule(text: dom.Text, typed: String, convert: => Boolean): Boolean = {
    text.data = text.data.substring(typed.length)
    convert
  }

  private def inlineRule(text: dom.Text, range: dom.Range): Boolean = {
    val typed = withPlainSpaces(text.data.substring(0, range.startOffset))

    inlineRules.view
      .flatMap { case (pattern, tag) => pattern.findFirstMatchIn(typed).map(found => (found, tag)) }
      .headOption
      .exists { case (found, tag) =>
        // `*a*` must not fire on the tail of `**a**`, which the strong rule has already declined as unbalanced.
        if (tag == "em" && found.start > 0 && typed.charAt(found.start - 1) == '*') false
        else {
          replaceWithMark(text, found.start, range.startOffset, found.group(1), tag)
          true
        }
      }
  }

  private def replaceWithMark(text: dom.Text, from: Int, to: Int, content: String, tag: String): Unit = {
    val document = dom.document
    val parent = text.parentNode
    val tail = text.data.substring(to)
    val mark = commands.element(tag, commands.inlineClass(tag))
    val after = document.createTextNode(tail)

    mark.textContent = content
    text.data = text.data.substring(0, from)
    parent.insertBefore(mark, text.nextSibling)
    parent.insertBefore(after, mark.nextSibling)
    caretAt(after, 0)
  }
}
