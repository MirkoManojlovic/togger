package com.togger.markdown

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/** A checkbox the reader ticked: which task item it was, and what it now says.
  *
  * The index is the item's position among the document's task items, in document order, because that is the only
  * identity available — the markdown library's AST carries no source positions.
  */
case class TaskToggle(index: Int, checked: Boolean)

/** Where a task marker sits in the source, and what state it is in. */
case class TaskMarker(line: Int, checked: Boolean)

/** Finds task markers in markdown source and flips them.
  *
  * The counterpart to the detection in [[TgMarkdownRender]]: that one reads markers out of the parsed document,
  * this one out of the raw text, and the two have to agree on both *what* is a task item and *what order* they
  * come in, since an index from the renderer is looked up here.
  *
  * Order agrees for free — the renderer walks the document depth-first in source order. Recognition is a line
  * scanner, which agrees with the parser everywhere except inside an indented code block, where four spaces make
  * text that looks like a task item into code. Rather than reimplement block parsing for that,
  * [[setChecked]] verifies the state it is about to change and declines when it does not match, so a
  * disagreement costs a click rather than the document.
  */
object TgMarkdownTasks {

  /** Deliberately close to what the parser accepts: any bullet or ordered marker, any indent, inside any depth of
    * block quote, and a space after the brackets — `- [x]` with nothing following it is not a task item to the
    * library either.
    */
  private val marker: Regex = """^[ \t]*(?:>[ \t]*)*(?:[-*+]|\d+[.)])[ \t]+\[([ xX])\][ \t]""".r

  private val fence: Regex = """^[ \t]*(?:```|~~~)""".r

  def markers(markdown: String): Seq[TaskMarker] = {
    val found = ListBuffer.empty[TaskMarker]
    var inFence = false

    lines(markdown).zipWithIndex.foreach { case (line, index) =>
      if (fence.findFirstIn(line).isDefined) inFence = !inFence
      else if (!inFence) marker.findFirstMatchIn(line).foreach(m => found += TaskMarker(index, m.group(1) != " "))
    }

    found.toSeq
  }

  /** The document with task item `index` set to `checked`.
    *
    * `None` when the index resolves to nothing, or to a marker already in that state — which is what an index
    * the renderer and the scanner disagree about looks like from here.
    */
  def setChecked(markdown: String, index: Int, checked: Boolean): Option[String] =
    markers(markdown)
      .lift(index)
      .filter(_.checked != checked)
      .map { found =>
        val source = lines(markdown)

        source.updated(found.line, withState(source(found.line), checked)).mkString("\n")
      }

  /** `split` with a negative limit, so a trailing newline survives as a final empty line and the document comes
    * back with the whitespace it went in with.
    */
  private def lines(markdown: String): Vector[String] = markdown.split("\n", -1).toVector

  private def withState(line: String, checked: Boolean): String =
    marker.findFirstMatchIn(line) match {
      case Some(found) => line.take(found.start(1)) + (if (checked) "x" else " ") + line.drop(found.start(1) + 1)
      case None        => line
    }
}
